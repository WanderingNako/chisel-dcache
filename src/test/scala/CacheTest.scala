import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.math.BigInt
import scala.util.Random
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

class CacheTest extends AnyFreeSpec with Matchers with ChiselSim {
  val addrWidth = 10
  val dataWidth = 32

  def genData() = BigInt(dataWidth, Random)
  def genAddr() = BigInt(addrWidth, Random)
  def genWen()  = Random.nextInt(2) == 0

  def goldenReference(testSeq: Seq[(Boolean, BigInt, BigInt, BigInt)]) = {
    val resultBuffer = ListBuffer[BigInt]()
    val memory = Map.empty[BigInt, BigInt]
    for(i <- 0 until testSeq.length) {
      if(testSeq(i)._1) {
        if(memory.contains(testSeq(i)._2)) memory(testSeq(i)._2) = testSeq(i)._3
        else memory += testSeq(i)._2 -> testSeq(i)._3
        resultBuffer += 0
      }
      else {
        resultBuffer += memory(testSeq(i)._4)
      }
    }
    resultBuffer.toList
  }

  "Cache should behave as it doesn't exist" in {
    simulate(new Cache(addrWidth, dataWidth)) { dut =>
      val maxSim = 1000
      val len = 100
      val wenSeq = Seq(true) ++ Seq.fill(len-1)(genWen())
      val waddrSeq = Seq.fill(len)(genAddr())
      val wdataSeq = Seq.fill(len)(genData())
      //waddrHistory holds all write addr until now
      val waddrHistory = ListBuffer[Seq[BigInt]]()
      val raddrBuffer = ListBuffer[BigInt]()
      for(i <- 0 until len) {
        if(i == 0) {
          waddrHistory += Seq(waddrSeq(i))
          raddrBuffer += 0
        }
        else if(wenSeq(i)) {
          waddrHistory += waddrHistory(i-1) ++ Seq(waddrSeq(i))
          raddrBuffer += 0
        }
        else {
          waddrHistory += waddrHistory(i-1)
          raddrBuffer += waddrHistory(i)(Random.nextInt(waddrHistory(i).length))
        }
      }
      val testValues = wenSeq.zip(waddrSeq).zip(wdataSeq).zip(raddrBuffer.toList).map{
        case (((a, b), c), d) => (a, b, c, d)
      }
      val resultSeq = goldenReference(testValues)

      //=======================Start Simulation========================
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
            if(!testValues(received)._1) dut.out.bits.rdata.expect(resultSeq(received).U)
            received += 1
          }
        }

        // Step the simulation forward.
        dut.clock.step()
        cycles += 1
      }
      println(s"Total cycles: ${cycles}")
    }
  }
}
