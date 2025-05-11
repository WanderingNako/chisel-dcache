import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import chisel3.util.PriorityEncoder

class TempTest extends AnyFreeSpec with Matchers with ChiselSim {

  "Temp Test" in {
    simulate(new Module {
      val io = IO(new Bundle {
        val in  = Input(Vec(4, Bool()))
        val hasInvalid = Output(Bool())
        val firstInvalidIdx = Output(UInt(2.W))
      })
      io.hasInvalid := io.in.reduce(_||_)
      io.firstInvalidIdx := PriorityEncoder(io.in)
    }) { dut =>
      //=======================Start Simulation========================
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)
      dut.clock.step()

      dut.io.in(0).poke(false.B)
      dut.io.in(1).poke(false.B)
      dut.io.in(2).poke(false.B)
      dut.io.in(3).poke(false.B)

      dut.io.hasInvalid.expect(false.B)
      if(dut.io.hasInvalid.peek().litToBoolean) dut.io.firstInvalidIdx.expect(3.U)
  }
  }
}
