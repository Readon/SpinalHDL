package spinal

import org.scalatest.funsuite.AnyFunSuite
import spinal.core._

import java.nio.file.{Path, Paths}

package object tester {
  val simWorkspacePath = "./simWorkspace"
  class SpinalAnyFunSuite extends AnyFunSuite {
    SpinalConfig.defaultTargetDirectory = simWorkspacePath
  }
  type SpinalTesterCocotbBase = scalatest.SpinalTesterCocotbBase
  type SpinalTesterGhdlBase = scalatest.SpinalTesterGhdlBase
  type SpinalFormalFunSuite = lib.formal.SpinalFormalFunSuite

  /**
   * Detects the SpinalHDL project root directory.
   *
   * This is useful when tests run in a sandbox/working directory different from the project root
   * (e.g., when using mill or other build tools).
   *
   * Detection order:
   *   1. If environment variable "SPINALHDL_PROJECT_ROOT" is defined, use that
   *   2. Walk up the directory tree looking for .git, build.mill, or build.sbt
   *   3. Fall back to current directory
   *
   * @return Absolute path to the project root directory
   */
  def projectRoot: String = {
    sys.env.get("SPINALHDL_PROJECT_ROOT").getOrElse {
      findProjectRoot(Paths.get(".").toAbsolutePath)
    }
  }

  /**
   * Recursively walks up the directory tree to find the project root.
   *
   * @param current The current directory to check
   * @return The absolute path string of the project root
   */
  @scala.annotation.tailrec
  private def findProjectRoot(current: Path): String = {
    val markers = Seq(".git", "build.mill", "build.sbt")
    val markerExists = markers.exists(marker => current.resolve(marker).toFile.exists())

    if (markerExists) {
      current.normalize().toString
    } else {
      val parent = current.getParent
      if (parent == null) {
        // Fallback: use current directory if we've reached the root
        Paths.get(".").toAbsolutePath.normalize().toString
      } else {
        findProjectRoot(parent)
      }
    }
  }
}
