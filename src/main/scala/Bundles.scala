import chisel3._

class CacheInputBundle(addrWidth: Int, dataWidth: Int) extends Bundle {
  val wen   = Bool()
  val waddr = UInt(addrWidth.W)
  val wdata = UInt(dataWidth.W)
  val raddr = UInt(addrWidth.W)
}

class CacheOutputBundle(dataWidth: Int) extends Bundle {
  val rdata = UInt(dataWidth.W)
}

class MemoryInputBundle(addrWidth: Int, dataWidth: Int) extends Bundle {
  val wen = Bool()
  val waddr = UInt(addrWidth.W)
  val wdata = UInt(dataWidth.W)
  val raddr = UInt(addrWidth.W)
}

class MemoryOutputBundle(dataWidth: Int) extends Bundle {
  val rdata = UInt(dataWidth.W)
}