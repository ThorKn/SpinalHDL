package spinal.lib.bus.amba4.axi

import spinal.core._
import spinal.lib._


case class Axi4WriteOnly(config: Axi4Config) extends Bundle with IMasterSlave with Axi4Bus{

  val aw = Stream(Axi4Aw(config))
  val w = Stream(Axi4W(config))
  val b = Stream(Axi4B(config))


  def writeCmd = aw
  def writeData = w
  def writeRsp = b

  def <<(that : Axi4) : Unit = that >> this
  def >> (that : Axi4) : Unit = {
    this.writeCmd drive that.writeCmd
    this.writeData drive that.writeData
    that.writeRsp drive this.writeRsp
  }

  def <<(that : Axi4WriteOnly) : Unit = that >> this
  def >> (that : Axi4WriteOnly) : Unit = {
    this.writeCmd drive that.writeCmd
    this.writeData drive that.writeData
    that.writeRsp drive this.writeRsp
  }

  def awValidPipe() : Axi4WriteOnly = {
    val sink = Axi4WriteOnly(config)
    sink.aw << this.aw.validPipe()
    sink.w  << this.w
    sink.b  >> this.b
    sink
  }

  def setIdle(): this.type = {
    this.writeCmd.setIdle()
    this.writeData.setIdle()
    this.writeRsp.setBlocked()
    this
  }

  def setBlocked(): this.type = {
    this.writeCmd.setBlocked()
    this.writeData.setBlocked()
    this.writeRsp.setIdle()
    this
  }

  def toAxi4(): Axi4 = {
    val ret = Axi4(config)
    this >> ret
  
    ret.readCmd.setIdle()
    ret.readRsp.setBlocked()

    ret
  }

  def toFullConfig(): Axi4WriteOnly = {
    val ret = Axi4WriteOnly(config.toFullConfig())
    ret << this
    ret
  }

  def pipelined(
    aw: StreamPipe = StreamPipe.NONE,
    w: StreamPipe = StreamPipe.NONE,
    b: StreamPipe = StreamPipe.NONE
  ): Axi4WriteOnly = {
    val ret = cloneOf(this)
    ret.aw << this.aw.pipelined(aw)
    ret.w << this.w.pipelined(w)
    ret.b.pipelined(b) >> this.b
    ret
  }

  override def asMaster(): Unit = {
    master(aw, w)
    slave(b)
  }

  def formalWrite(operation: (Bool) => spinal.core.internals.AssertStatement) = new Area {
    import spinal.core.formal._
    val reset = ClockDomain.current.isResetActive

    when(reset || past(reset)) {
      operation(aw.valid === False)
      operation(w.valid === False)
    }

    val maxSize = log2Up(config.bytePerWord)
    val len = Reg(UInt(8 bits)) init (0)
    val size = Reg(UInt(3 bits)) init (maxSize)
    when(aw.fire) {
      if (config.useLen) len := aw.len else len := 0
      if (config.useSize) size := aw.size else size := 0
    }
  }

  def formalResponse(operation: (Bool) => spinal.core.internals.AssertStatement) = new Area {
    import spinal.core.formal._
    val reset = ClockDomain.current.isResetActive

    when(reset || past(reset)) {
      operation(b.valid === False)
    }
  }

  def withAsserts(maxStallCycles: Int = 0) = new Area {
    aw.withAsserts()
    aw.withTimeoutAssumes(maxStallCycles)
    w.withAsserts()
    w.withTimeoutAssumes(maxStallCycles)
    b.withAssumes()
    b.withTimeoutAsserts(maxStallCycles)

    when(aw.valid) {
      aw.payload.withAsserts()
    }
    val write = formalWrite(assert)
    formalResponse(assume)
  }

  def withAssumes(maxStallCycles: Int = 0) = new Area {
    aw.withAssumes()
    aw.withTimeoutAsserts(maxStallCycles)
    w.withAssumes()
    w.withTimeoutAsserts(maxStallCycles)
    b.withAsserts()
    b.withTimeoutAssumes(maxStallCycles)

    when(aw.valid) {
      aw.payload.withAssumes()
    }
    val write = formalWrite(assume)
    formalResponse(assert)
  }

  def withCovers() = {
    aw.withCovers(2)
    when(aw.fire) {
      aw.payload.withCovers()
    }
    w.withCovers(2)
    when(w.fire) {
      w.payload.withCovers()
    }
    b.withCovers(2)
    when(b.fire) {
      b.payload.withCovers()
    }
  }
}
