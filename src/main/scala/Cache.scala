import chisel3._
import chisel3.util.Decoupled

class CacheInputBundle(addrWidth: Int, dataWidth: Int) extends Bundle {
  val wen   = Bool()
  val waddr = UInt(addrWidth.W)
  val wdata = UInt(dataWidth.W)
  val raddr = UInt(addrWidth.W)
}

class CacheOutputBundle(dataWidth: Int) extends Bundle {
  val rdata = UInt(dataWidth.W)
}

class Cache(addrWidth: Int, dataWidth: Int) extends Module {
  val in  = IO(Flipped(Decoupled(new CacheInputBundle(addrWidth, dataWidth))))
  val out = IO(Decoupled(new CacheOutputBundle(dataWidth)))
  
  val wen         = Reg(Bool())
  val waddr       = Reg(UInt())
  val wdata       = Reg(UInt())
  val raddr       = Reg(UInt())
  val rdata       = Reg(UInt())
  val busy        = RegInit(false.B)
  val resultValid = RegInit(false.B)
  val memory      = Mem(1 << addrWidth, UInt(dataWidth.W))

  in.ready := !busy
  out.valid := resultValid
  out.bits.rdata := rdata
  
  when(busy){
    when(!resultValid) {
      when(wen) {
        memory(waddr) := wdata
        printf(cf"writing... 0x${wdata}%x to addr 0x${waddr}%x\n")
      }.otherwise {
        printf(cf"reading... addr 0x${raddr}%x is 0x${memory(raddr)}%x\n")
        rdata := memory(raddr)
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
      printf(cf"Receive wen=${req.wen}%b, waddr=0x${req.waddr}%x, wdata=0x${req.wdata}%x, raddr=0x${req.raddr}%x\n")
    }
  }
}