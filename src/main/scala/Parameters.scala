import chisel3._
import chisel3.util._

case class Parameters(
  addrWidth: Int = 16, 
  dataWidth: Int = 32
){
  require(dataWidth % 8 == 0, "DataWidth must be multiple of 8.")
  def dataBytes = dataWidth / 8
}

object Parameters {
  implicit val defaultParams: Parameters = Parameters()
}

case class CacheParams (
  nSets: Int = 2,
  nWays: Int = 2,
  blockBytes: Int = 64
)(implicit p: Parameters) {
  require(tagWidth >= 0, "AddrWidth must >= indexWidth + wayWidth + offset")
  require(isPow2(nSets), "nSets must be a power of two")
  require(isPow2(nWays), "nWays must be a power of two")
  require(isPow2(blockBytes), "blockBytes must be a power of two")
  def indexWidth = log2Up(nSets)
  def offset     = log2Up(blockBytes)
  def tagWidth   = p.addrWidth - indexWidth - offset
}

trait HasCacheParams {
  val cacheParams: CacheParams = CacheParams()
}

case class MemoryParams (
  nBytes : Int = 1 << 20
)

trait HasMemoryParams {
  val memoryParams: MemoryParams = MemoryParams()
}