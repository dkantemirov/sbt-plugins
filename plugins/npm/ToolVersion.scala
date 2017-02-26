package sbt.plugins.npm

case class ToolVersion(numbers: List[Int], suffix: Option[String]) {
  def major: Int = numbers.head
  def minor: Option[Int] = numbers.drop(1).headOption
  def patch: Option[Int] = numbers.drop(2).headOption

  override def toString: String = (numbers, suffix) match {
    case (n, Some(s)) if n.nonEmpty => "%s-%s".format(n.mkString("."), s)
    case (n, None) if n.nonEmpty => n.mkString(".")
    case (n, Some(s)) if n.isEmpty => s
    case _ => ""
  }
}

object ToolVersion {
  def apply(version: String): ToolVersion = {
    try {
      val numbers = version.split("\\-", 2).head.split("\\.", -1).map(_.toInt).toList
      val suffix = version.split("\\-", 2).tail.headOption
      ToolVersion(numbers, suffix)
    } catch {
      case _: Exception => ToolVersion(Nil, Some(version))
    }
  }
}

object DefaultToolVersionOrdering extends Ordering[ToolVersion] {
  override def compare(a: ToolVersion, b: ToolVersion): Int = {
    def compareNumberSequence(ns1: Seq[Int], ns2: Seq[Int]): Int = (ns1, ns2) match {
      case (Nil, Nil) => 0
      case (n1 :: tail1, Nil) => +1
      case (Nil, n2 :: tail2) => -1
      case (n1 :: tail1, n2 :: tail2) =>
        if (n1 < n2) -1
        else if (n1 > n2) +1
        else compareNumberSequence(tail1, tail2)
    }

    compareNumberSequence(a.numbers, b.numbers) match {
      case res if res != 0 => res
      case 0 =>
        (a.suffix, b.suffix) match {
          case (None, None) => 0
          case (Some(s1), None) => -1
          case (None, Some(s2)) => +1
          case (Some(s1), Some(s2)) =>
            if (s1 < s2) -1
            else if (s1 > s2) +1
            else 0
        }
    }
  }
}