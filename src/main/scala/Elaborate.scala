// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage
object Elaborate extends App {
  ChiselStage.emitSystemVerilogFile(
    new Cache(10, 32),
    args,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable")
  )
}