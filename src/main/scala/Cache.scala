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
  val waddr       = Reg(UInt(p.addrWidth.W))
  val wdata       = Reg(UInt(p.dataWidth.W))
  val raddr       = Reg(UInt(p.addrWidth.W))
  val rdata       = Reg(UInt(p.dataWidth.W))
  val busy        = RegInit(false.B)
  val resultValid = RegInit(false.B)
  val mem_valid   = RegInit(false.B)
  val mem_ready   = RegInit(true.B)
  val recover     = RegInit(true.B) //Write Allocate need to recover mem block when miss
  val recData     = Reg(Vec(cacheParams.blockBytes, UInt(8.W)))
  val writeBack   = RegInit(false.B)

  //val meta        = Mem(cacheParams.nSets, Vec(cacheParams.nWays, new MetaBundle))
  val meta = RegInit(VecInit(Seq.fill(cacheParams.nSets)(
    VecInit(Seq.fill(cacheParams.nWays)(MetaBundle.default()))
  )))
  val data = Mem(cacheParams.nSets, Vec(cacheParams.nWays, Vec(cacheParams.blockBytes, UInt(8.W))))

  val tag     = Wire(UInt(cacheParams.tagWidth.W))
  val index   = Wire(UInt(cacheParams.indexWidth.W))
  val offset  = Wire(UInt(cacheParams.offset.W))
  val recAddr = Wire(UInt(p.addrWidth.W))
  when(wen) {
    tag     := waddr(p.addrWidth-1, p.addrWidth-cacheParams.tagWidth)
    index   := waddr(cacheParams.indexWidth+cacheParams.offset-1, cacheParams.offset)
    offset  := waddr(cacheParams.offset-1, 0)
    recAddr := Cat(waddr(p.addrWidth-1, cacheParams.offset), 0.U(cacheParams.offset.W))
  }.otherwise {
    tag     := raddr(p.addrWidth-1, p.addrWidth-cacheParams.tagWidth)
    index   := raddr(cacheParams.indexWidth+cacheParams.offset-1, cacheParams.offset)
    offset  := raddr(cacheParams.offset-1, 0)
    recAddr := Cat(raddr(p.addrWidth-1, cacheParams.offset), 0.U(cacheParams.offset.W))
  }

  val hasInvalid      = Wire(Bool())
  val firstInvalidIdx = Wire(UInt())
  hasInvalid      := meta(index).exists((x) => {!x.valid})
  firstInvalidIdx := meta(index).indexWhere((x) => {!x.valid})

  val hit     = Wire(Bool())
  val hitIdx  = Wire(UInt())
  hit     := meta(index).exists((x)=>{x.tag === tag && x.valid})
  hitIdx  := meta(index).indexWhere((x)=>{x.tag === tag && x.valid})

  val evictIdx = Wire(UInt())
  evictIdx := Mux(hasInvalid, firstInvalidIdx, 0.U)

  val rHitData  = Wire(Vec(p.dataBytes, UInt(8.W)))
  val rMissData = Wire(Vec(p.dataBytes, UInt(8.W)))
  for(i <- 0 until p.dataBytes) {
    rHitData(i) := data(index)(hitIdx)(offset + i.U)
    rMissData(i) := recData(offset + i.U)
  }

  val wmask = Wire(UInt(cacheParams.blockWidth.W))
  val wBlockData = Wire(UInt(cacheParams.blockWidth.W))
  val wRecData = Wire(UInt(cacheParams.blockWidth.W))
  val wRecBytes = Wire(Vec(cacheParams.blockBytes, UInt(8.W)))
  wmask := ~0.U(p.dataWidth.W) << (offset * 8.U)
  wBlockData := wdata << (offset * 8.U)
  wRecData := (Cat(recData) & ~wmask) | (wBlockData & wmask)
  for(i <- 0 until cacheParams.blockBytes) {
    wRecBytes(i) := wRecData((i+1)*8-1,i*8)
  }

  cpu_in.ready := !busy
  cpu_out.valid := resultValid
  cpu_out.bits.rdata := rdata

  mem_in.ready := mem_ready
  mem_out.valid := mem_valid
  mem_out.bits := DontCare
  
  when(busy){
    when(!resultValid) {
      when(hit) {
        when(wen) {
          meta(index)(hitIdx).dirty := true.B
          for(i <- 0 until p.dataBytes) {
            data(index)(hitIdx)(offset + i.U) := wdata((i+1)*8-1, i*8)
          }
        }.otherwise {
          rdata := Cat(rHitData.reverse)
        }
        resultValid := true.B
      }.otherwise {
        //Recover block from memory
        when(recover) {
          mem_valid := true.B
          mem_out.bits.wen := false.B
          mem_out.bits.raddr := recAddr
          when(mem_in.valid){
            mem_valid := false.B
            recData := mem_in.bits.rdata
            recover := false.B
            writeBack := !hasInvalid && meta(index)(evictIdx).dirty
          }
        }.elsewhen(writeBack) {
          mem_valid := true.B
          mem_out.bits.wen := true.B
          mem_out.bits.waddr := Cat(meta(index)(evictIdx).tag, index, 0.U(cacheParams.offset.W))
          mem_out.bits.wdata := data(index)(evictIdx)
          when(mem_in.valid) {
            mem_valid := false.B
            writeBack := false.B
          }
        }.otherwise {
          meta(index)(evictIdx).valid := true.B
          meta(index)(evictIdx).dirty := wen
          meta(index)(evictIdx).tag   := tag
          data(index)(evictIdx)       := Mux(wen, wRecBytes, recData)
          rdata := Cat(rMissData.reverse)
          resultValid := true.B
        }
      }
    }
    when(cpu_out.ready && resultValid) {
      busy := false.B
      resultValid := false.B
      recover := true.B
    }
  }.otherwise{
    when(cpu_in.valid) {
      val req = cpu_in.deq()
      wen   := req.wen
      waddr := req.waddr
      wdata := req.wdata
      raddr := req.raddr
      busy := true.B
      //printf(cf"Receive wen=${req.wen}, waddr=0x${req.waddr}%x, wdata=0x${req.wdata}%x, raddr=0x${req.raddr}%x\n")
    }
  }
}