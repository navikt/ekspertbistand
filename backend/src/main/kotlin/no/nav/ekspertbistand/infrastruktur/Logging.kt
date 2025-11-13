package no.nav.ekspertbistand.infrastruktur

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.Appender
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.ContextAware
import ch.qos.logback.core.spi.ContextAwareBase
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.spi.LifeCycle
import net.logstash.logback.appender.LogstashTcpSocketAppender
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.ekspertbistand.infrastruktur.NaisEnvironment.clusterName
import org.slf4j.Logger
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import java.lang.reflect.Proxy

const val TEAM_LOGS = "TEAM_LOGS"
val teamLogsMarker = MarkerFactory.getMarker(TEAM_LOGS)

/* used by resources/META-INF/services/ch.qos.logback.classic.spi */
class LogConfig : ContextAwareBase(), Configurator {

    override fun configure(lc: LoggerContext): ExecutionStatus {
        val rootAppender = MaskingAppender().setup(lc) {
            appender = ConsoleAppender<ILoggingEvent>().setup(lc) {
                encoder = LogstashEncoder().setup(lc) {
                    isIncludeMdc = true
                }
                addFilter(object : Filter<ILoggingEvent>() {
                    override fun decide(event: ILoggingEvent) = when {
                        (event.markerList ?: emptyList()).contains(teamLogsMarker) -> FilterReply.DENY
                        else -> FilterReply.NEUTRAL
                    }
                })
            }
        }

        lc.getLogger("org.flywaydb.core.internal").level = Level.WARN

        lc.getLogger(ROOT_LOGGER_NAME).apply {
            level = basedOnEnv(
                prod = { Level.INFO },
                dev = { Level.INFO },
                other = { Level.INFO }
            )
            addAppender(rootAppender)

            if (clusterName.isNotEmpty()) {
                addAppender(LogstashTcpSocketAppender().setup(lc) {
                    this.name = "TEAMLOGS"
                    addDestination("team-logs.nais-system:5170")
                    this.encoder = LogstashEncoder().setup(lc) {
                        this.customFields = """{
                        |"google_cloud_project":"${System.getenv("GOOGLE_CLOUD_PROJECT")}",
                        |"nais_namespace_name":"${System.getenv("NAIS_NAMESPACE")}",
                        |"nais_pod_name":"${System.getenv("NAIS_POD_NAME")}",
                        |"nais_container_name":"${System.getenv("NAIS_APP_NAME")}"
                        |}""".trimMargin()
                    }
                })
            }
        }


        return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY
    }
}

private fun <T> T.setup(context: LoggerContext, body: T.() -> Unit = {}): T
        where T : ContextAware,
              T : LifeCycle {
    this.context = context
    this.body()
    this.start()
    return this
}


class MaskingAppender : AppenderBase<ILoggingEvent>() {

    var appender: Appender<ILoggingEvent>? = null

    override fun append(event: ILoggingEvent) {
        appender?.doAppend(
            object : ILoggingEvent by event {
                override fun getFormattedMessage(): String? =
                    mask(event.formattedMessage)

                override fun getThrowableProxy(): IThrowableProxy? {
                    if (event.throwableProxy == null) {
                        return null
                    }
                    return object : IThrowableProxy by event.throwableProxy {
                        override fun getMessage(): String? =
                            mask(event.throwableProxy.message)
                    }
                }

            }
        )
    }

    companion object {
        val FNR = Regex("""(^|\D)\d{11}(?=$|\D)""")
        val ORGNR = Regex("""(^|\D)\d{9}(?=$|\D)""")
        val EPOST = Regex("""[\w.%+-]+@[\w.%+-]+\.[a-zA-Z]{2,}""")
        val PASSWORD = Regex("""password=.*(?=$)""")

        fun mask(string: String?): String? {
            return string?.let {
                FNR.replace(it, "$1***********")
                    .replace(ORGNR, "$1*********")
                    .replace(EPOST, "********")
                    .replace(PASSWORD, "password=********")
            }
        }
    }
}

inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.qualifiedName)
inline fun <reified T> T.teamLogger(): Logger =
    markerProxy(LoggerFactory.getLogger(T::class.qualifiedName), teamLogsMarker)

@Suppress("UNCHECKED_CAST")
fun markerProxy(delegate: Logger, marker: Marker): Logger {
    // optimization: precompute methods that take Marker as first parameter
    val markerMethods = delegate.javaClass.methods
        .filter { it.parameterTypes.firstOrNull() == Marker::class.java }

    return Proxy.newProxyInstance(
        Logger::class.java.classLoader,
        arrayOf(Logger::class.java)
    ) { _, method, args ->
        when {
            // prevent direct use of Marker in marked logger
            args?.firstOrNull() is Marker ->
                error("Direct use of Marker in marked logger is not allowed: ${method.name}")

            // handle equals/hashCode/toString properly (Proxy quirk)
            method.name == "equals" && args?.size == 1 ->
                delegate == Proxy.getInvocationHandler(args[0])

            method.name == "hashCode" && (args == null || args.isEmpty()) ->
                delegate.hashCode()

            method.name == "toString" && (args == null || args.isEmpty()) ->
                "MarkedLogger(delegate=${delegate.name}, marker=$marker)"

            // inject marker into method call
            else -> {
                val argCount = args?.size ?: 0
                val markerMethod = markerMethods.find {
                    it.name == method.name && it.parameterTypes.size == argCount + 1
                } ?: error("No marker overload found for method '${method.name}' with ${argCount + 1} params on ${delegate.javaClass.name}")

                val withMarkerArgs = if (args == null) arrayOf(marker) else arrayOf(marker, *args)
                markerMethod.invoke(delegate, *withMarkerArgs)
            }
        }
    } as Logger
}
