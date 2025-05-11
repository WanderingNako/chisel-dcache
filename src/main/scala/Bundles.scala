import chisel3._

class CPUInputBundle(implicit p: Parameters) extends Bundle {
  val wen   = Bool()
  val waddr = UInt(p.addrWidth.W)
  val wdata = UInt(p.dataWidth.W)
  val raddr = UInt(p.addrWidth.W)
}

class CPUOutputBundle(implicit p: Parameters) extends Bundle {
  val rdata = UInt(p.dataWidth.W)
}

class MemoryInputBundle(implicit p: Parameters) extends Bundle {
  val wen = Bool()
  val waddr = UInt(p.addrWidth.W)
  val wdata = UInt(p.dataWidth.W)
  val raddr = UInt(p.addrWidth.W)
}

class MemoryOutputBundle(implicit p: Parameters) extends Bundle {
  val rdata = UInt(p.dataWidth.W)
}


class MetaBundle(implicit p: Parameters) extends Bundle with HasCacheParams {
  val valid = Bool()
  val tag   = UInt(cacheParams.tagWidth.W)
  val dirty = Bool()
}

object MetaBundle {
  def default() : MetaBundle = {
    val m = Wire(new MetaBundle)
    m.valid := false.B
    m.tag   := 0.U
    m.dirty := false.B
    m
  }
}