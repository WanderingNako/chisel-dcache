import chisel3._
import chisel3.util.Decoupled

class Cache(addrWidth: Int, dataWidth: Int) extends Module {
  //CPU IO
  val cpu_in  = IO(Flipped(Decoupled(new CPUInputBundle(addrWidth, dataWidth))))
  val cpu_out = IO(Decoupled(new CPUOutputBundle(dataWidth)))
  //MEMORY IO
  val mem_in  = IO(Flipped(Decoupled(new MemoryOutputBundle(dataWidth))))
  val mem_out = IO(Decoupled(new MemoryInputBundle(addrWidth, dataWidth)))
  
  val wen         = Reg(Bool())
  val waddr       = Reg(UInt())
  val wdata       = Reg(UInt())
  val raddr       = Reg(UInt())
  val rdata       = Reg(UInt())
  val busy        = RegInit(false.B)
  val resultValid = RegInit(false.B)
  val mem_valid   = RegInit(false.B)
  val mem_ready   = RegInit(true.B)

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
    }
  }
}