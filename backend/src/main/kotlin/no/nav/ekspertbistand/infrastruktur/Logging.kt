package no.nav.ekspertbistand.infrastruktur

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.Appender
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.spi.ContextAware
import ch.qos.logback.core.spi.ContextAwareBase
import ch.qos.logback.core.spi.LifeCycle
import net.logstash.logback.appender.LogstashTcpSocketAppender
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.kv
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.ekspertbistand.infrastruktur.NaisEnvironment.clusterName
import org.slf4j.LoggerFactory

/* used by resources/META-INF/services/ch.qos.logback.classic.spi */
class LogConfig : ContextAwareBase(), Configurator {
    override fun configure(lc: LoggerContext): ExecutionStatus {
        val rootAppender = MaskingAppender().setup(lc) {
            appender = ConsoleAppender<ILoggingEvent>().setup(lc) {
                encoder = LogstashEncoder().setup(lc) {
                    /**
                     * isIncludeStructuredArguments settes til false for å unngå at
                     * dette sendes til vanlig log. Dette logges kun til team-logs
                     * i LogstashTcpSocketAppender nedenfor.
                     * se [TeamLogCtx]
                     */
                    isIncludeStructuredArguments = false
                    isIncludeMdc = true
                }
            }
        }

        lc.getLogger("org.flywaydb.core.internal").level = Level.WARN

        lc.getLogger(Logger.ROOT_LOGGER_NAME).apply {
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

inline fun <reified T> T.logger(): org.slf4j.Logger = LoggerFactory.getLogger(T::class.qualifiedName)


/**
 * eksempel bruk:
 * log.info("FOO", *TeamLogCtx.of(sensitiveDataMap))
 */
object TeamLogCtx {
    fun of(ctx: Map<String, String>) = ctx.map {
        kv(it.key, it.value)
    }.toTypedArray<StructuredArgument>()
}

