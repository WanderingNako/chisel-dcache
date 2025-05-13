import chisel3._
import chisel3.util._

class System(implicit val p: Parameters) extends Module {
  val in  = IO(Flipped(Decoupled(new CPUInputBundle)))
  val out = IO(Decoupled(new CPUOutputBundle))

  val cache = Module(new Cache)
  val mem   = Module(new Memory)

  require(cache.cacheParams.blockBytes == mem.memoryParams.nBurstBytes, 
          "Cache Block size must be equal to Memory burst size!")

  require(p.addrWidth <= log2Up(mem.memoryParams.nBytes), 
          "CPU AddrWidth must less than Memory indexWidth!")
  cache.cpu_in <> in
  out <> cache.cpu_out
  cache.mem_in <> mem.out
  mem.in <> cache.mem_out
}