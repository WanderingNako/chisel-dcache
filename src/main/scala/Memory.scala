import chisel3._
import chisel3.util.Decoupled

class Memory(implicit p: Parameters) extends Module with HasMemoryParams {
  val in  = IO(Flipped(Decoupled(new MemoryInputBundle)))
  val out = IO(Decoupled(new MemoryOutputBundle))

  val wen         = Reg(Bool())
  val waddr       = Reg(UInt(p.addrWidth.W))
  val wdata       = Reg(Vec(memoryParams.nBurstBytes, UInt(8.W)))
  val raddr       = Reg(UInt(p.addrWidth.W))
  val rdata       = Reg(Vec(memoryParams.nBurstBytes, UInt(8.W)))
  val busy        = RegInit(false.B)
  val resultValid = RegInit(false.B)
  val memory      = Mem(memoryParams.nBytes, UInt(8.W))

  in.ready := !busy
  out.valid := resultValid
  out.bits.rdata := rdata
  
  when(busy){
    when(!resultValid) {
      when(wen) {
        for(i <- 0 until memoryParams.nBurstBytes){
          memory.write(waddr+i.U, wdata(i))
        }
      }.otherwise {
        for(i <- 0 until memoryParams.nBurstBytes){
          rdata(i) := memory.read(raddr+i.U)
        }
      }
      resultValid := true.B
    }
    when(out.ready && resultValid) {
      busy := false.B
      resultValid := false.B
    }
  }.otherwise{
    when(in.valid) {
      val req = in.deq()
      wen   := req.wen
      waddr := req.waddr
      wdata := req.wdata
      raddr := req.raddr
      busy  := true.B
    }
  }
}