package io.kotless.parser.ktor.processor.route

import io.kotless.*
import io.kotless.Webapp.Events
import io.kotless.dsl.ktor.Kotless
import io.kotless.parser.ktor.processor.action.GlobalActionsProcessor
import io.kotless.parser.processor.ProcessorContext
import io.kotless.parser.processor.SubTypesProcessor
import io.kotless.parser.processor.config.EntrypointProcessor
import io.kotless.parser.processor.permission.PermissionsProcessor
import io.kotless.parser.utils.errors.error
import io.kotless.parser.utils.psi.*
import io.kotless.utils.TypedStorage
import io.kotless.utils.everyNMinutes
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext

internal object DynamicRoutesProcessor : SubTypesProcessor<Unit>() {
    private val functions = mapOf(
        "io.ktor.routing.get" to HttpMethod.GET,
        "io.ktor.routing.post" to HttpMethod.POST,
        "io.ktor.routing.put" to HttpMethod.PUT,
        "io.ktor.routing.patch" to HttpMethod.PATCH,
        "io.ktor.routing.delete" to HttpMethod.DELETE,
        "io.ktor.routing.head" to HttpMethod.HEAD,
        "io.ktor.routing.options" to HttpMethod.OPTIONS
    )

    override val klasses = setOf(Kotless::class)

    override fun mayRun(context: ProcessorContext) = context.output.check(GlobalActionsProcessor) && context.output.check(EntrypointProcessor)

    override fun process(files: Set<KtFile>, binding: BindingContext, context: ProcessorContext) {
        val globalPermissions = context.output.get(GlobalActionsProcessor).permissions
        val entrypoint = context.output.get(EntrypointProcessor).entrypoint

        processClasses(files, binding) { klass, _ ->
            klass.visitNamedFunctions(filter = { func -> func.name == Kotless::prepare.name }) { func ->
                func.visitCallExpressionsWithReferences(binding = binding, filter = { it.getFqName(binding) in functions.keys}) { element ->
                    //TODO-tanvd fix routing between few methods
                    val outer = getDynamicPath(element, binding)

                    val method = functions[element.getFqName(binding)] ?: error(element, "Unknown Ktor HTTP handler definition")

                    val permissions = PermissionsProcessor.process(element, binding) + globalPermissions

                    val path = URIPath(outer, element.getArgument("path", binding).asPath(binding))
                    val name = "${path.parts.joinToString(separator = "_")}_${method.name}"

                    val key = TypedStorage.Key<Lambda>()
                    val function = Lambda(name, context.jar, entrypoint, context.lambda, permissions)

                    context.resources.register(key, function)
                    context.routes.register(Webapp.ApiGateway.DynamicRoute(method, path, key))

                    if (context.config.optimization.autowarm.enable) {
                        context.events.register(
                            Events.Scheduled(name, everyNMinutes(context.config.optimization.autowarm.minutes), ScheduledEventType.Autowarm, key)
                        )
                    }
                }
            }
        }
    }

    private fun getDynamicPath(element: KtElement, binding: BindingContext): URIPath {
        val previous = element.parents<KtCallExpression>()
        val routeCalls = previous.filter { it.getFqName(binding) == "io.ktor.routing.route" }
        val path = routeCalls.map {
            it.getArgumentOrNull("path", binding)?.asString(binding) ?: ""
        }.filter { it.isNotBlank() }.toList().reversed()
        return URIPath(path)
    }
}
