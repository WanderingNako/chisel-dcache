import chisel3._

case class Parameters(
  addrWidth: Int = 10, 
  dataWidth: Int = 32
)

object Parameters {
  implicit val defaultParams: Parameters = Parameters()
}

case class CacheParams (
  nSets: Int = 64,
  nWays: Int = 4,
  blockBytes: Int = 64
)

trait HasCacheParams {
  val cacheParams: CacheParams = CacheParams()
}