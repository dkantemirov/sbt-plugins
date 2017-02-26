package sbt.plugins.npm

case class JsonConfiguration(
  initialState: InitialState,
  apiAuthHeader: ApiAuthHeader,
  apiPath: ApiPath,
  logOutput: LogOutput,
  logLevel: LogLevel
)

abstract case class JsonConfigurationSetting(name: String, value: Option[String])

object JsonConfigurationSetting {
  final val apiHostnameName = "apiHostname"
  final val apiPathName = "apiPath"
  final val apiPortName = "apiPort"
  final val apiAuthHeaderName = "apiAuthHeader"
  final val initialStateName = "initialState"
  final val logOutputName = "logOutput"
  final val logLevelName = "logLevel"

  def apply(name: String, value: Option[String]): JsonConfigurationSetting = name match {
    case `apiHostnameName` => new ApiHostname(value)
    case `apiPathName` => new ApiPath(value)
    case `apiPortName` => new ApiPort(value)
    case `apiAuthHeaderName` => new ApiAuthHeader(value)
    case `initialStateName` => new InitialState(value)
    case `logOutputName` => new LogOutput(value)
    case `logLevelName` => new LogLevel(value)
  }
}

class ApiHostname(value: Option[String]) extends JsonConfigurationSetting(JsonConfigurationSetting.apiHostnameName, value)
class ApiPath(value: Option[String]) extends JsonConfigurationSetting(JsonConfigurationSetting.apiPathName, value)
class ApiPort(value: Option[String]) extends JsonConfigurationSetting(JsonConfigurationSetting.apiPortName, value)
class ApiAuthHeader(value: Option[String]) extends JsonConfigurationSetting(JsonConfigurationSetting.apiAuthHeaderName, value)
class InitialState(value: Option[String]) extends JsonConfigurationSetting(JsonConfigurationSetting.initialStateName, value)
class LogOutput(value: Option[String]) extends JsonConfigurationSetting(JsonConfigurationSetting.logOutputName, value)
class LogLevel(value: Option[String]) extends JsonConfigurationSetting(JsonConfigurationSetting.logLevelName, value)