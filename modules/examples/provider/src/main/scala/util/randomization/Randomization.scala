package util.randomization

import org.scalacheck.rng.Seed
import org.scalacheck.{Arbitrary, Gen}

import java.util.UUID
import scala.reflect.ClassTag
import scala.util.{Failure, Random, Success, Try}

final case class RandomDataException(private val message: String = "", private val cause: Throwable = None.orNull) extends Exception(message, cause)

trait Randomization {

  def randomFloat: Float = Random.nextFloat()

  def randomDouble: Double = Random.nextDouble()

  def randomLong: Long = randomLong()

  def randomLong(min: Long = 0, max: Long = 10): Long = Random.nextLong(max - min) + min
  def randomInt: Int = randomInt()

  def randomInt(min: Int = 0, max: Int = 10): Int = Random.nextInt(max - min) + min

  def randomString: String = randomString()

  def randomString(length: Int = 10): String = (Random.alphanumeric take length).mkString("")

  def randomUUID: UUID = UUID.randomUUID

  def random[T: Arbitrary](using ct: ClassTag[T]): T = randomSuchThat[T](1, _ => true).head

  def random[T: Arbitrary](size: Int)(using ct: ClassTag[T]): Seq[T] = randomSuchThat[T](size, _ => true)

  def randomSuchThat[T: Arbitrary](n: Int, f: T => Boolean)(using ct: ClassTag[T]): Seq[T] = {
    Try(
      Gen.listOfN(n, Arbitrary.arbitrary[T].suchThat(f)).apply(Gen.Parameters.default, Seed.random())
    ) match {
      case Success(Some(v)) => v
      case Success(None)    => throw RandomDataException(s"Could not generate a random value for ${ct.runtimeClass.getSimpleName}")
      case Failure(exception) =>
        throw RandomDataException(
          s"Failed to generate a random value for ${ct.runtimeClass.getSimpleName}: ${exception.getLocalizedMessage}",
          exception
        )
    }
  }
}

object Randomization extends Randomization
