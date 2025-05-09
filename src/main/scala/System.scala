import chisel3._
import chisel3.util.Decoupled

class System(addrWidth: Int, dataWidth: Int) extends Module {
  val in  = IO(Flipped(Decoupled(new CPUInputBundle(addrWidth, dataWidth))))
  val out = IO(Decoupled(new CPUOutputBundle(dataWidth)))

  val cache = Module(new Cache(addrWidth, dataWidth))
  val mem   = Module(new Memory(addrWidth, dataWidth))

  cache.cpu_in <> in
  out <> cache.cpu_out
  cache.mem_in <> mem.out
  mem.in <> cache.mem_out
}