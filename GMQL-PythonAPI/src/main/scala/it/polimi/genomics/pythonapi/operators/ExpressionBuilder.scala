package it.polimi.genomics.pythonapi.operators
import it.polimi.genomics.core.DataStructures.RegionAggregate._
import it.polimi.genomics.core.DataStructures._
import it.polimi.genomics.core.ParsingType
import it.polimi.genomics.core.ParsingType.PARSING_TYPE
import it.polimi.genomics.pythonapi.PythonManager
/**
  * Created by Luca Nanni on 11/04/17.
  * Email: luca.nanni@mail.polimi.it
  */

/**
  * This class enables the creation of complex region and meta conditions
  * based on the desires of the user
  * */
class ExpressionBuilder(index : Int) {

  def getMetaOperation(symbol : String) =
  {
    symbol match {
      case "GT" => MetadataCondition.META_OP.GT
      case "GTE" => MetadataCondition.META_OP.GTE
      case "LT" => MetadataCondition.META_OP.LT
      case "LTE" => MetadataCondition.META_OP.LTE
      case "EQ" => MetadataCondition.META_OP.EQ
      case "NOTEQ" => MetadataCondition.META_OP.NOTEQ
      case _ => throw new IllegalArgumentException(symbol + " is not a MetadataCondition")
    }
  }

  def getRegionOperation(symbol : String) =
  {
    symbol match {
      case "GT" => RegionCondition.REG_OP.GT
      case "GTE" => RegionCondition.REG_OP.GTE
      case "LT" => RegionCondition.REG_OP.LT
      case "LTE" => RegionCondition.REG_OP.LTE
      case "EQ" => RegionCondition.REG_OP.EQ
      case "NOTEQ" => RegionCondition.REG_OP.NOTEQ
      case _ => throw new IllegalArgumentException(symbol + " is not a RegionCondition")
    }
  }

  def createRegionPredicate(name: String, operation: String, value : String) =
  {
    val regOp = getRegionOperation(operation)
    // get the Variable
    val variable = PythonManager.getVariable(this.index)

    name match {
      case "chr" => RegionCondition.ChrCondition(value)
      case "right" => RegionCondition.RightEndCondition(regOp,value.toLong)
      case "left" => RegionCondition.LeftEndCondition(regOp, value.toLong)
      case "strand" => RegionCondition.StrandCondition(value)
      case "start" => RegionCondition.StartCondition(regOp, value.toLong)
      case "stop" => RegionCondition.StopCondition(regOp, value.toLong)
      case _ =>
        val field = variable.get_field_by_name(name)

        //casting
        val type_field = variable.get_type_by_name(name)


        if(field.isDefined & type_field.isDefined) {
          val parsed_value = parseValueByType(value, type_field.get)
          RegionCondition.Predicate(field.get,regOp, parsed_value)
        }
        else {
          throw new IllegalArgumentException("The region field " + name + " is not defined " +
            "or its type is wrong")
        }
    }
  }

  def parseValueByType(value: String, value_type: PARSING_TYPE): Any = {
    value_type match {
      case ParsingType.STRING => value
      case ParsingType.CHAR => value
      case ParsingType.LONG => value.toLong
      case ParsingType.INTEGER => value.toInt
      case ParsingType.DOUBLE => value.toDouble
      case _ => value
    }
  }

  def createMetaPredicate(name : String, operation: String, value : String) = {
    val metaOp = getMetaOperation(operation)

    MetadataCondition.Predicate(name, metaOp, value)
  }

  def createMetaBinaryCondition(pred1 : MetadataCondition.MetadataCondition, condition : String,
                                pred2 : MetadataCondition.MetadataCondition): MetadataCondition.MetadataCondition =
  {
    condition match {
      case "AND" => MetadataCondition.AND(pred1, pred2)
      case "OR" => MetadataCondition.OR(pred1, pred2)
      case _ => throw new IllegalArgumentException(condition + " is not a valid MetaBinaryCondition")
    }
  }

  def createMetaUnaryCondition(pred: MetadataCondition.MetadataCondition,
                               condition: String): MetadataCondition.MetadataCondition =
  {
    condition match {
      case "NOT" => MetadataCondition.NOT(pred)
      case _ => throw new IllegalArgumentException(condition + " is not a valid MetaUnaryCondition")
    }
  }

  def createRegionBinaryCondition(pred1 : RegionCondition.RegionCondition, condition : String,
                                  pred2 : RegionCondition.RegionCondition) : RegionCondition.RegionCondition =
  {
    condition match {
      case "AND" => RegionCondition.AND(pred1, pred2)
      case "OR" => RegionCondition.OR(pred1, pred2)
      case _ => throw new IllegalArgumentException(condition + " is not a valid RegionBinaryCondition")
    }
  }

  def createRegionUnaryCondition(pred: RegionCondition.RegionCondition,
                               condition: String): RegionCondition.RegionCondition =
  {
    condition match {
      case "NOT" => RegionCondition.NOT(pred)
      case _ => throw new IllegalArgumentException(condition + " is not a valid RegionUnaryCondition")
    }
  }

  def createRegionExtension(name : String, node: RENode): RegionFunction =
  {
    val regionExtensionFactory = PythonManager.getServer.implementation.regionExtensionFactory
    regionExtensionFactory.get(node,Left(name))
  }

  def getRegionsToRegion(functionName: String, newAttrName: String, argument: String): RegionsToRegion = {
    val regionsToRegionFactory = PythonManager.getServer.implementation.mapFunctionFactory
    val variable = PythonManager.getVariable(this.index)
    val field_number = variable.get_field_by_name(argument)
    // by default it's a COUNT operation
    var res : RegionsToRegion = regionsToRegionFactory.get("COUNT", Option(newAttrName))
    if(field_number.isDefined) {
      res = regionsToRegionFactory.get(functionName, field_number.get, Option(newAttrName))
    }
    res.output_name = Option(newAttrName)
    res
  }

  def getRENode(name : String) : RENode =
  {
    name match {
      case "start" => RESTART()
      case "stop" => RESTOP()
      case "left" => RELEFT()
      case "right" => RERIGHT()
      case "chr" => RECHR()
      case "strand" => RESTRAND()
      case _ => {
        val variable = PythonManager.getVariable(this.index)
        val pos = variable.get_field_by_name(name)
        if(pos.isDefined) {
          REPos(pos.get)
        }
        else {
          REPos(0)
        }
      }
    }
  }

  def getREType(typename : String, value : String) : RENode = {
    typename match {
      case "string" => REStringConstant(value)
      case _ => REFloat(value.toDouble)
    }
  }

  def getBinaryRegionExpression(node1: RENode, operator: String, node2: RENode) : RENode =
  {
    operator match {
      case "ADD" => READD(node1, node2)
      case "DIV" => REDIV(node1, node2)
      case "SUB" => RESUB(node1, node2)
      case "MUL" => REMUL(node1, node2)
    }
  }

  def getRegionsToMeta(functionName: String, newAttrName: String, argument: String): RegionsToMeta = {
    val regionsToMetaFactory = PythonManager.getServer.implementation.extendFunctionFactory
    val variable = PythonManager.getVariable(this.index)
    val field_number = variable.get_field_by_name(argument)
    // by default it's a COUNT operation
    var res : RegionsToMeta = regionsToMetaFactory.get("COUNT", Option(newAttrName))
    if(field_number.isDefined) {
      res = regionsToMetaFactory.get(functionName, field_number.get, Option(newAttrName))
    }
    res
  }

}