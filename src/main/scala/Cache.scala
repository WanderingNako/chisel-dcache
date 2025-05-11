import chisel3._
import chisel3.util.Decoupled
import chisel3.util.PriorityEncoder

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
  val maskInvalid     = Wire(Vec(cacheParams.nWays, Bool()))
  val hasInvalid      = Wire(Bool())
  val firstInvalidIdx = Wire(UInt())

  maskInvalid     := meta(index).map(!_.valid)
  hasInvalid      := maskInvalid.reduce(_ || _)
  firstInvalidIdx := PriorityEncoder(maskInvalid)
  printf(cf"first:=${firstInvalidIdx}, index:=${index}\n")

  when(wen) {
    index := waddr(cacheParams.indexWidth+cacheParams.offset-1, cacheParams.offset)
  }.otherwise {
    index := raddr(cacheParams.indexWidth+cacheParams.offset-1, cacheParams.offset)
  }

  cpu_in.ready := !busy
  cpu_out.valid := resultValid
  cpu_out.bits.rdata := rdata

  mem_in.ready := mem_ready
  mem_out.valid := mem_valid
  mem_out.bits.wen := wen
  mem_out.bits.waddr := waddr
  mem_out.bits.wdata := wdata
  mem_out.bits.raddr := raddr
  
  when(busy){
    when(!resultValid) {
      when(wen) {
        when(hasInvalid) {
          meta(index)(firstInvalidIdx).valid := true.B
          meta(index)(firstInvalidIdx).tag   := waddr(p.addrWidth-1, p.addrWidth-cacheParams.tagWidth)
          meta(index)(firstInvalidIdx).dirty := false.B
        }
      }
      mem_valid := true.B
      when(mem_in.valid) {
        rdata := mem_in.bits.rdata
        mem_valid := false.B
        resultValid := true.B
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