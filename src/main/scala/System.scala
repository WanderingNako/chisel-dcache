import chisel3._
import chisel3.util.Decoupled

class System(implicit val p: Parameters) extends Module {
  val in  = IO(Flipped(Decoupled(new CPUInputBundle)))
  val out = IO(Decoupled(new CPUOutputBundle))

  val cache = Module(new Cache)
  val mem   = Module(new Memory)

  cache.cpu_in <> in
  out <> cache.cpu_out
  cache.mem_in <> mem.out
  mem.in <> cache.mem_out
}