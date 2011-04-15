package jenkins.test

import java.io.{File, FileOutputStream, InputStream}
import java.net.{ServerSocket, URL}
import scala.util.DynamicVariable
import org.apache.commons.io.IOUtils
import org.junit.Assume
import org.junit.rules.{MethodRule, TemporaryFolder}
import org.junit.runners.model.{FrameworkMethod, Statement}

import org.apache.maven.artifact.versioning.{ArtifactVersion, DefaultArtifactVersion}

object JenkinsMain {
  private val versionZero: ArtifactVersion = new DefaultArtifactVersion("0")
  private val version = new DynamicVariable[ArtifactVersion](null)
  object NoOpStatement extends Statement {
    override def evaluate(): Unit = Assume.assumeTrue(false)
  }
  
  private def findFreePort: Int = {
    val ss = new ServerSocket(0)
    try {
      ss.getLocalPort
    } finally {
      ss.close
    }
  }
}

class JenkinsMain extends MethodRule {
  private val tempFolder = new TemporaryFolder
  private var process: Option[Process] = None
  private var port = JenkinsMain.findFreePort
  private var startCount = 0
  private var javaOpts = List("-Xmx128M")
  
  private def parseVersionAnnotation[T <: { def version(): String }](str: Option[T]): Int =
    str map { _.version } map { new DefaultArtifactVersion(_) } map { _.compareTo(JenkinsMain.version.value) } getOrElse {0}
  
  override def apply(base: Statement, method: FrameworkMethod, target: AnyRef): Statement = {
    val since = parseVersionAnnotation(Option(method.getAnnotation(classOf[Since])))
    val until = parseVersionAnnotation(Option(method.getAnnotation(classOf[Until])))
    val currentJenkinsVersion = JenkinsMain.version.value
    
    if (since > 0 || until < 0) {
      JenkinsMain.NoOpStatement
    } else {
      tempFolder(base, method, target) // TODO: Wrap base with logic first
    }
  }
  
  private def ioCopy(is: InputStream, file: File) = new Thread(new Runnable() {
    override def run = {
      val os = new FileOutputStream(file)
      try {
        IOUtils.copy(is, os)
      } finally {
        IOUtils.closeQuietly(os)
      }
    }
  }).start
  
  def jenkinsHome: File = tempFolder.newFolder("jenkins")
  
  def start = {
    process map { ignore => throw new IllegalStateException("Jenkins is already started") }
    
    startCount += 1
    val builder = new ProcessBuilder("java")
    val command = builder.command
    (javaOpts ::: List("-jar", "jenkins.war", "--httpPort=" + port)) foreach { command.add(_) }
    builder.environment.put("JENKINS_HOME", jenkinsHome.getAbsolutePath)
    process = Some(builder.start)
    val p = process.get
    ioCopy(p.getInputStream, tempFolder.newFile("jenkins." + startCount + ".out"))
    ioCopy(p.getErrorStream, tempFolder.newFile("jenkins." + startCount + ".err"))
  }
  
  def stop = {
    val p = process getOrElse { throw new IllegalStateException("Jenkins has not been started") }
    process = None
    p.destroy
  }
  
  def baseUrl = new URL("http", "127.0.0.1", port, "/")
}
