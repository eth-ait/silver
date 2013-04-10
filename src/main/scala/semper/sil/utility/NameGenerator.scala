package semper.sil.utility

import semper.sil.ast.utility.Consistency
import util.matching.Regex
import scala.concurrent.Lock

/**
 * Interface for a class that can generate unique names.
 *
 * @author Bernhard Brodowsky
 */
trait NameGenerator {

  /**
   * Creates a different identifier every time it is called.
   */
  def createUniqueIdentifier(s: String): String

  /**
   * Returns a different string every time it is called. If possible, it
   * returns the input string, otherwise, it appends a number at the end.
   * If the input is a valid identifier, the output is also a valid identifier.
   * Calling this method directly would not be thread safe.
   */
  def createUnique(s: String): String

  /**
   * Takes an arbitrary string and returns a valid identifier that
   * resembles that string. Special characters are replaced or removed.
   */
  def createIdentifier(s: String): String

  /**
   * Returns a sub context for names. The names within that context can not conflict with
   * names in the global context, but there may be several subcontexts with names which
   * conflict whith each other.
   */
  def createSubContext(): NameGenerator

}

/**
 * Default Implementation for the name generator.
 *
 * @author Bernhard Brodowsky
 */
trait DefaultNameGenerator extends NameGenerator {
  private var deadLockId = 0

  private val idLock = new Object()

  val globalContext = new NameContext()

  /**
   * The string used to separate the preferred identifier from the number postfix.
   */
  def separator: String

  /**
   * The default identifier that is used in case of empty identifiers.
   */
  def defaultIdent = "v"

  /**
   * A regular expression that matches any character allowed as a first char in a valid
   * identifier.
   */
  def firstCharacter: Regex

  /**
   * A regular expression that matches the characters allowed (except in the first position).
   */
  def otherCharacter: Regex

  /**
   * A set of names which are forbidden as valid identifiers.
   */
  def reservedNames: Set[String]

  /** Special letters that can be replaced with a specific string inside identifiers */
  lazy val replacableLetters = Map(
    'Α' -> "Alpha",
    'Β' -> "Beta",
    'Γ' -> "Gamma",
    'Δ' -> "Delta",
    'Ε' -> "Epsilon",
    'Ζ' -> "Zeta",
    'Η' -> "Eta",
    'Θ' -> "Theta",
    'Ι' -> "Iota",
    'Κ' -> "Kappa",
    'Λ' -> "Lambda",
    'Μ' -> "My",
    'Ν' -> "Ny",
    'Ξ' -> "Xi",
    'Ο' -> "Omikron",
    'Π' -> "Pi",
    'Ρ' -> "Rho",
    'Σ' -> "Sigma",
    'Τ' -> "Tau",
    'Υ' -> "Ypsilon",
    'Φ' -> "Phi",
    'Χ' -> "Chi",
    'Ψ' -> "Psi",
    'Ω' -> "Omega",
    'α' -> "alpha",
    'β' -> "beta",
    'γ' -> "gamma",
    'δ' -> "delta",
    'ε' -> "epsilon",
    'ζ' -> "zeta",
    'η' -> "eta",
    'θ' -> "theta",
    'ι' -> "iota",
    'κ' -> "kappa",
    'λ' -> "lambda",
    'μ' -> "my",
    'ν' -> "ny",
    'ξ' -> "xi",
    'ο' -> "omikron",
    'π' -> "pi",
    'ρ' -> "rho",
    'ς' -> "varsigma",
    'σ' -> "sigma",
    'τ' -> "tau",
    'υ' -> "ypsilon",
    'φ' -> "phi",
    'χ' -> "chi",
    'ψ' -> "psi",
    'ω' -> "omega",
    '+' -> "plus",
    '-' -> "minus",
    '*' -> "times",
    '/' -> "divided",
    '%' -> "mod",
    '!' -> "bang",
    '<' -> "less" ,
    '>' -> "greater",
    '=' -> "eq")

  private def newId() = idLock.synchronized {
    deadLockId += 1
    deadLockId
  }

  def createUniqueIdentifier(s: String) = globalContext.createUniqueIdentifier(s)

  def createUnique(s: String) = globalContext.createUnique(s)

  /**
   * A context inside which the names have to be unique. They also have to be unique within all enclosing contexts,
   * but there might be independent contexts which contain the same names.
   */
  class NameContext(enclosingContexts: List[NameContext] = Nil) extends NameGenerator {
    private var directSubContexts = List[NameContext]()

    private val identCounters = collection.mutable.HashMap[String, Int]()

    private val deadLockId = newId()

    private val contexts = this :: enclosingContexts

    private val lock = new Lock

    private def subContexts: List[NameContext] = directSubContexts ++ directSubContexts.flatMap(_.subContexts)

    // TODO If performance is an issue, these can be cached until the next call of createSubContext().
    private def conflictingContexts = (contexts ++ subContexts).sortBy(_.deadLockId)

    def createSubContext() = {
      val c = new NameContext(contexts)
      directSubContexts = c :: directSubContexts
      c
    }

    def createUnique(s: String) = {
      val cc = conflictingContexts
      cc.foreach(_.lock.acquire())
      val res = if (!cc.exists(_.identCounters.contains(s))) {
        identCounters.put(s, 0)
        s
      } else {
        val counters = cc.map { c =>
          c.identCounters.get(s) match {
            case None => -1
            case Some(x) => x
          }
        }
        var counter = counters.max + 1
        var newS = s + separator + counter.toString
        while (cc.exists(_.identCounters.contains(newS))) {
          counter += 1
          newS = s + separator + counter.toString
        }
        identCounters.put(newS, counter)
        newS
      }
      cc.foreach(_.lock.release())
      res
    }

    def createUniqueIdentifier(s: String) = createUnique(createIdentifier(s))

    def createIdentifier(s: String) = DefaultNameGenerator.this.createIdentifier(s)

  }

  def createIdentifier(input: String) = {
    if (input.isEmpty) {
      defaultIdent
    } else {
      val builder = new StringBuilder
      val first = input.head
      if (!firstCharacter.findFirstIn(first.toString).isDefined && !replacableLetters.contains(first)) {
        builder.append(defaultIdent)
      }
      input foreach {
        c =>
          if (otherCharacter.findFirstIn(c.toString).isDefined) {
            builder.append(c)
          } else if (replacableLetters.contains(c)) {
            builder.append(replacableLetters(c))
          }
      }
      var res = builder.result
      while (reservedNames.contains(res)) {
        res = defaultIdent + res
      }
      res
    }
  }

  def createSubContext() = globalContext.createSubContext()

}
