import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.math.BigInt
import scala.util.Random
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

class CacheTest extends AnyFreeSpec with Matchers with ChiselSim with HasMemoryParams{
  "Cache should behave as it doesn't exist" in {
    simulate(new System) { dut =>
      val maxSim = 10000
      val len = 500
      val wenSeq = Seq(true) ++ Seq.fill(len-1)(genWen())
      val waddrSeq = Seq.fill(len)(genAddr(dut.p.addrWidth))
      val wdataSeq = Seq.fill(len)(genData())
      //waddrHistory holds all write addr until now
      val waddrHistory = ListBuffer[Seq[Int]]()
      val raddrBuffer = ListBuffer[Int]()
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
      val resultSeq = goldenReference(testValues, memoryParams.nBytes, dut.p.dataWidth / 8)

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

  def genWen() = Random.nextInt(2) == 0
  def genData() = Random.nextInt(Int.MaxValue)
  //Addr is 4byte align to avoid different Blocks (Depends on DataBytes)
  def genAddr(addrWidth: Int) = Random.nextInt(1 << addrWidth) & ~0x3

  def int2Bytes(value: Int): Array[Byte] = {
    Array(
      value.toByte,
      (value >>> 8).toByte,
      (value >>> 16).toByte,
      (value >>> 24).toByte
    )
  }

  def bytes2Int(bytes: Array[Byte]): Int = {
    require(bytes.length == 4, "Byte array must be exactly 4 bytes for Int")
    
    ((bytes(3) & 0xFF) << 24) |
    ((bytes(2) & 0xFF) << 16) |
    ((bytes(1) & 0xFF) << 8)  |
    (bytes(0) & 0xFF)
  }

  def goldenReference(testSeq: Seq[(Boolean, Int, Int, Int)], msize: Int, dbytes: Int) = {
    val resultBuffer = ListBuffer[Int]()
    val memory = new Array[Byte](msize)
    val resultArray = new Array[Byte](dbytes)
    for(i <- 0 until testSeq.length) {
      if(testSeq(i)._1) {
        var dataArray: Array[Byte] = int2Bytes(testSeq(i)._3)
        for(j <- 0 until dbytes) {
          memory(testSeq(i)._2 + j) = dataArray(j)
        }
        resultBuffer += 0
      }
      else {
        for(j <- 0 until dbytes) {
          resultArray(j) = memory(testSeq(i)._4 + j)
        }
        resultBuffer += bytes2Int(resultArray)
      }
    }
    resultBuffer.toList
  }
}
