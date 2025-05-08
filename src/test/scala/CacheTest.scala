import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.math.BigInt
import scala.util.Random

class CacheTest extends AnyFreeSpec with Matchers with ChiselSim {
  val addrWidth = 10
  val dataWidth = 32

  def genData() = BigInt(dataWidth, Random)
  def genAddr() = BigInt(addrWidth, Random)
  def genWen()  = Random.nextInt(2) == 0

  "Cache should behave as it doesn't exist" in {
    simulate(new Cache(addrWidth, dataWidth)) { dut =>
      println(genData(), genAddr(), genWen())
      val maxSim = 100
      val len = 2
      val wenSeq = Seq.fill(len)(genWen())
      val waddrSeq = Seq.fill(len)(genAddr())
      val wdataSeq = Seq.fill(len)(genData())
      val raddrSeq = Seq(0, 0)
      val testValues = wenSeq.zip(waddrSeq).zip(wdataSeq).zip(raddrSeq).map{
        case (((a, b), c), d) => (a, b, c, d)
      }
      //val testValues = Seq((true, 0, 1, 0), (false, 0, 1, 0))
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)
      dut.clock.step()

      var sent, received, cycles: Int = 0
      while (sent != len || received != len) {
        assert(cycles <= maxSim, "timeout reached")

        if (sent < len) {
          dut.in.valid.poke(true.B)
          dut.in.bits.wen.poke(testValues(sent)._1.B)
          dut.in.bits.waddr.poke(testValues(sent)._2.U)
          dut.in.bits.wdata.poke(testValues(sent)._3.U)
          dut.in.bits.raddr.poke(testValues(sent)._4.U)
          if (dut.in.ready.peek().litToBoolean) {
            sent += 1
          }
        }
        if (received < len) {
          dut.out.ready.poke(true.B)
          if (dut.out.valid.peek().litToBoolean) {
            if(!testValues(received)._1) dut.out.bits.rdata.expect(1.U)
            received += 1
          }
        }

        // Step the simulation forward.
        dut.clock.step()
        cycles += 1
      }
    }
  }
}
