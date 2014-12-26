package eventstreams

/*
 * Copyright 2014 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import _root_.core.events.EventOps.symbolToEventField
import eventstreams.core.Tools.configHelper
import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.plugins.essentials._
import eventstreams.support.TestHelpers
import play.api.libs.json._

class SplitInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with SplitInstructionConstants {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> ".*?(<s>.+?)(<s>.*)",
      CfgFSource -> "source"
    )
  }
  
  import eventstreams.plugins.essentials.SplitInstructionConstants._

  s"SplitInstruction" should s"not build without $CfgFSource" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> ".*?(<s>.+?)(<s>.*)"
    )

    shouldNotBuild()
  }

  it should s"not build without $CfgFPattern" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFSource -> "source"
    )

    shouldNotBuild()
  }

  it should s"not build with invalid $CfgFPattern" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "replace",
      CfgFSource -> "value",
      CfgFPattern -> "("
    )

    shouldNotBuild()
  }

  it should s"be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  it should "split basic sequence into 2 events" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>first<s>second<s>incompletethird")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("<s>first"))
      result(1) ~> 'source should be (Some("<s>second"))
    }
  }

  it should "properly use remainder in all cases - scenario 1" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(Json.obj("source" -> "rest<s>bla")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("<s>incompletethirdrest"))
    }
  }

  it should "properly use remainder in all cases - scenario 2" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(Json.obj("source" -> "<s>bla")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("<s>incompletethird"))
    }
  }

  it should "properly use remainder in all cases - scenario 3" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(Json.obj("source" -> "more<s")) { result =>
      result should have size 0
    }
  }

  it should "properly use remainder in all cases - scenario 4" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(Json.obj("source" -> "more<s")) { result => }
    expectN(Json.obj("source" -> ">bla")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("<s>incompletethirdmore"))
    }
  }
  it should "properly use remainder in all cases - scenario 5" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(Json.obj("source" -> "more<s")) { result => }
    expectN(Json.obj("source" -> ">bla<s>another")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("<s>incompletethirdmore"))
      result(1) ~> 'source should be (Some("<s>bla"))
    }
  }
  it should "properly use remainder in all cases - scenario 6" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(Json.obj("source" -> "more<s")) { result => }
    expectN(Json.obj("source" -> ">bla<s>")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("<s>incompletethirdmore"))
      result(1) ~> 'source should be (Some("<s>bla"))
    }
  }
  it should "properly use remainder in all cases - scenario 7" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(Json.obj("source" -> "more<s")) { result => }
    expectN(Json.obj("source" -> ">bla<s>")) { result => }
    expectN(Json.obj("source" -> "another")) { result =>
      result should have size 0
    }
  }

  it should "properly use remainder in all cases - scenario 8" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(Json.obj("source" -> "more<s")) { result => }
    expectN(Json.obj("source" -> ">bla<s>")) { result => }
    expectN(Json.obj("source" -> "another")) { result => }
    expectN(Json.obj("source" -> "another<s>")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("<s>anotheranother"))
    }
  }
  it should "operate in utf8" in new WithBasicConfig {
    expectN(Json.obj("source" -> "bla<s>первый<s>второй<s>incompletethird")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("<s>первый"))
      result(1) ~> 'source should be (Some("<s>второй"))
    }
  }

  trait WithMultilineConfig extends WithSimpleInstructionBuilder with SplitInstructionConstants {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> "(?ms).*?^(.+?)(^.*)",
      CfgFSource -> "source"
    )
  }

  it should "support advanced patterns - multiline split" in new WithMultilineConfig {
    expectN(Json.obj("source" -> "bla\nfirst\nsecond\nincompletethird")) { result =>
      result should have size 3
      result(0) ~> 'source should be (Some("bla"))
      result(1) ~> 'source should be (Some("first"))
      result(2) ~> 'source should be (Some("second"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 2" in new WithMultilineConfig {
    expectN(Json.obj("source" -> "bla\nfirst\nsecond\nincompletethird\n")) { result =>
      result should have size 3
      result(0) ~> 'source should be (Some("bla"))
      result(1) ~> 'source should be (Some("first"))
      result(2) ~> 'source should be (Some("second"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 3" in new WithMultilineConfig {
    expectN(Json.obj("source" -> "bla\nfirst\nsecond\nincompletethird")) { result => }
    expectN(Json.obj("source" -> "\nsome")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("incompletethird"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 4" in new WithMultilineConfig {
    expectN(Json.obj("source" -> "bla\nfirst\nsecond\nincompletethird")) { result => }
    expectN(Json.obj("source" -> "\n")) { result => }
    expectN(Json.obj("source" -> "\n")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("incompletethird"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 5" in new WithMultilineConfig {
    expectN(Json.obj("source" -> "bla\nfirst\nsecond\nincompletethird")) { result => }
    expectN(Json.obj("source" -> "\n")) { result => }
    expectN(Json.obj("source" -> "another\nsome")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("incompletethird"))
      result(1) ~> 'source should be (Some("another"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 6" in new WithMultilineConfig {
    expectN(Json.obj("source" -> "bla\nfirst\nsecond\nincompletethird\n")) { result => }
    expectN(Json.obj("source" -> "\n")) { result => }
    expectN(Json.obj("source" -> "another")) { result =>
      result should have size 0
    }
    expectN(Json.obj("source" -> "another")) { result =>
      result should have size 0
    }
    expectN(Json.obj("source" -> "xx\nxx")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("anotheranotherxx"))
    }
  }

  trait WithNMONConfig extends WithSimpleInstructionBuilder with SplitInstructionConstants {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> "(?ms).*?^(ZZZZ,T\\d+.+?)(^ZZZZ,T.*)",
      CfgFSource -> "source"
    )
  }

  it should "support advanced patterns - NMON split" in new WithNMONConfig {
    expectN(Json.obj("source" -> "\n\nZZZZ,T00001\nCPU\nWHATEVER\nZZZZ,T00002")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("ZZZZ,T00001\nCPU\nWHATEVER"))
    }

  }

}
