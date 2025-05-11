import chisel3._
import chisel3.util.Decoupled
import chisel3.util.PriorityEncoder
import chisel3.util.Cat

class Cache(implicit p: Parameters) extends Module with HasCacheParams{
  //CPU IO
  val cpu_in  = IO(Flipped(Decoupled(new CPUInputBundle)))
  val cpu_out = IO(Decoupled(new CPUOutputBundle))
  //MEMORY IO
  val mem_in  = IO(Flipped(Decoupled(new MemoryOutputBundle)))
  val mem_out = IO(Decoupled(new MemoryInputBundle))
  
  val wen         = Reg(Bool())
  val waddr       = Reg(UInt())
  val wdata       = Reg(UInt())
  val raddr       = Reg(UInt())
  val rdata       = Reg(UInt())
  val busy        = RegInit(false.B)
  val resultValid = RegInit(false.B)
  val mem_valid   = RegInit(false.B)
  val mem_ready   = RegInit(true.B)

  val meta        = Mem(cacheParams.nSets, Vec(cacheParams.nWays, new MetaBundle))
  val data        = Mem(cacheParams.nSets, Vec(cacheParams.nWays, UInt(p.dataWidth.W)))

  val index           = Wire(UInt())
  when(wen) {
    index := waddr(cacheParams.indexWidth+cacheParams.offset-1, cacheParams.offset)
  }.otherwise {
    index := raddr(cacheParams.indexWidth+cacheParams.offset-1, cacheParams.offset)
  }


  val maskInvalid     = Wire(Vec(cacheParams.nWays, Bool()))
  val hasInvalid      = Wire(Bool())
  val firstInvalidIdx = Wire(UInt())
  maskInvalid     := meta(index).map(!_.valid)
  hasInvalid      := maskInvalid.reduce(_||_)
  firstInvalidIdx := PriorityEncoder(maskInvalid)

  val maskHit = Wire(Vec(cacheParams.nWays, Bool()))
  val hit     = Wire(Bool())
  val hitIdx  = Wire(UInt())
  maskHit := meta(index).map(_.tag === raddr(p.addrWidth-1, p.addrWidth-cacheParams.tagWidth))
  hitIdx  := PriorityEncoder(maskHit)
  hit     := maskHit.reduce(_||_) && meta(index)(hitIdx).valid

  val evictMeta = Wire(new MetaBundle)
  val evictData = Wire(UInt(p.dataWidth.W))
  evictMeta := meta(index)(0)
  evictData := data(index)(0)

  
  //printf(cf"maskInvalid_0=${maskInvalid(0)}, maskInvalid_1=${maskInvalid(1)}, maskInvalid_2=${maskInvalid(2)}, maskInvalid_3=${maskInvalid(3)}\n")
  //printf(cf"first:=${firstInvalidIdx}, index:=${index}, hasInvalid:=${hasInvalid}\n")

  

  cpu_in.ready := !busy
  cpu_out.valid := resultValid
  cpu_out.bits.rdata := rdata

  mem_in.ready := mem_ready
  mem_out.valid := mem_valid
  mem_out.bits.wen := wen
  mem_out.bits.waddr := Cat(evictMeta.tag, index, waddr(cacheParams.offset-1, 0))
  mem_out.bits.wdata := evictData
  mem_out.bits.raddr := raddr
  
  when(busy){
    when(!resultValid) {
      //Write
      when(wen) {
        //Don't need to evict
        when(hit) {
          meta(index)(hitIdx).dirty := true.B
          data(index)(hitIdx)       := wdata
          resultValid := true.B
        }
        .elsewhen(hasInvalid) {
          meta(index)(firstInvalidIdx).valid := true.B
          meta(index)(firstInvalidIdx).tag   := waddr(p.addrWidth-1, p.addrWidth-cacheParams.tagWidth)
          meta(index)(firstInvalidIdx).dirty := false.B
          data(index)(firstInvalidIdx)       := wdata
          resultValid := true.B
        }.otherwise {
          //Need to evict
          when(evictMeta.dirty) {
            mem_valid := true.B
            when(mem_in.valid) {
              mem_valid := false.B
              evictMeta.valid := false.B
            }
          }.otherwise {
            evictMeta.tag   := waddr(p.addrWidth-1, p.addrWidth-cacheParams.tagWidth)
            evictMeta.dirty := true.B
            evictData       := wdata
            resultValid     := true.B
          }
        }
      }.otherwise {
        //Read
        when(hit) {
          rdata := data(index)(hitIdx)
          resultValid := true.B
        }.otherwise {
          mem_valid := true.B
          when(mem_in.valid) {
            mem_valid := false.B
            rdata := mem_in.bits.rdata
            resultValid := true.B
          }

        }
      }
    }
    when(cpu_out.ready && resultValid) {
      busy := false.B
      resultValid := false.B
    }
  }.otherwise{
    when(cpu_in.valid) {
      val req = cpu_in.deq()
      wen   := req.wen
      waddr := req.waddr
      wdata := req.wdata
      raddr := req.raddr
      busy := true.B
      printf(cf"Receive wen=${req.wen}, waddr=0x${req.waddr}%x, wdata=0x${req.wdata}%x, raddr=0x${req.raddr}%x\n")
    }
  }
}