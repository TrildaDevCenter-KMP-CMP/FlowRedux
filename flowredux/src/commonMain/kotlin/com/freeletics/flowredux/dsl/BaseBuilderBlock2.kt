package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.sideeffects.CollectWhile
import com.freeletics.flowredux.sideeffects.OnAction
import com.freeletics.flowredux.sideeffects.OnActionStartStateMachine
import com.freeletics.flowredux.sideeffects.OnEnter
import com.freeletics.flowredux.sideeffects.OnEnterStartStateMachine
import com.freeletics.flowredux.sideeffects.SideEffect
import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import com.freeletics.mad.statemachine.StateMachine
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
@FlowReduxDsl
public abstract class BaseBuilderBlock2<InputState : S, S : Any, A : Any> internal constructor() {
    internal abstract val isInState: SideEffectBuilder.IsInState<S>

    internal open fun sideEffectIsInState(initialState: InputState) = SideEffect.IsInState<S> {
        isInState.check(it)
    }

    internal val sideEffectBuilders = ArrayList<SideEffectBuilder<InputState, S, A>>()

    /**
     * Triggers every time an action of type [SubAction] is dispatched while the state machine is
     * in this state.
     *
     * An ongoing [handler] is cancelled when leaving this state. [executionPolicy] is used to
     * determine the behavior when a new [SubAction] is dispatched while the previous [handler]
     * execution is still ongoing. By default an ongoing [handler] is cancelled when leaving this
     * state or when a new [SubAction] is dispatched.
     */
    public inline fun <reified SubAction : A> on(
        executionPolicy: ExecutionPolicy = ExecutionPolicy.CANCEL_PREVIOUS,
        noinline handler: suspend State<InputState>.(action: SubAction) -> ChangedState<S>,
    ) {
        on(SubAction::class, executionPolicy, handler)
    }

    @PublishedApi
    internal fun <SubAction : A> on(
        actionClass: KClass<SubAction>,
        executionPolicy: ExecutionPolicy,
        handler: suspend State<InputState>.(action: SubAction) -> ChangedState<S>,
    ) {
        sideEffectBuilders += SideEffectBuilder(isInState) {
            OnAction(
                isInState = sideEffectIsInState(it),
                subActionClass = actionClass,
                executionPolicy = executionPolicy,
                handler = { action, state -> state.handler(action) },
            )
        }
    }

    /**
     *  An effect is a way to do some work without changing the state.
     *  A typical use case would be trigger navigation as some sort of side effect or
     *  triggering analytics.
     *  This is the "effect counterpart" to handling actions that you would do with [on].
     *
     * An ongoing [handler] is cancelled when leaving this state. [executionPolicy] is used to
     * determine the behavior when a new [SubAction] is dispatched while the previous [handler]
     * execution is still ongoing. By default an ongoing [handler] is cancelled when leaving this
     * state or when a new [SubAction] is dispatched.
     */
    public inline fun <reified SubAction : A> onActionEffect(
        executionPolicy: ExecutionPolicy = ExecutionPolicy.CANCEL_PREVIOUS,
        noinline handler: suspend ReadOnlyState<InputState>.(action: SubAction) -> Unit,
    ) {
        onActionEffect(SubAction::class, executionPolicy, handler)
    }

    @PublishedApi
    internal fun <SubAction : A> onActionEffect(
        actionClass: KClass<SubAction>,
        executionPolicy: ExecutionPolicy,
        handler: suspend ReadOnlyState<InputState>.(action: SubAction) -> Unit,
    ) {
        on(
            actionClass = actionClass,
            executionPolicy = executionPolicy,
            handler = {
                handler(it)
                NoStateChange
            },
        )
    }

    /**
     * Triggers every time the state machine enters this state.
     * It only triggers again if the surrounding `in<State>` condition is met and will only
     * re-trigger if `in<State>` condition returned false and then true again.
     *
     * An ongoing [handler] is cancelled when leaving this state.
     */
    public fun onEnter(
        handler: suspend State<InputState>.() -> ChangedState<S>,
    ) {
        sideEffectBuilders += SideEffectBuilder(isInState) { initialState ->
            OnEnter(
                isInState = sideEffectIsInState(initialState),
                initialState = initialState,
                handler = handler,
            )
        }
    }

    /**
     * An effect is a way to do some work without changing the state.
     * A typical use case is to trigger navigation as some sort of side effect or
     * triggering analytics or do logging.
     *
     * This is the "effect counterpart" of [onEnter] and follows the same logic when it triggers
     * and when it gets canceled.
     */
    public fun onEnterEffect(
        handler: suspend ReadOnlyState<InputState>.() -> Unit,
    ) {
        onEnter {
            handler()
            NoStateChange
        }
    }

    /**
     * Triggers every time the state machine enters this state. The passed [flow] will be collected
     * and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [executionPolicy] is used to determine the behavior when a new emission from [flow] arrives
     * before the previous [handler] invocation completed. By default [ExecutionPolicy.ORDERED]
     * is applied.
     */
    public fun <T> collectWhileInState(
        flow: Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.ORDERED,
        handler: suspend State<InputState>.(item: T) -> ChangedState<S>,
    ) {
        sideEffectBuilders += SideEffectBuilder(isInState) {
            CollectWhile(
                isInState = sideEffectIsInState(it),
                flow = flow,
                executionPolicy = executionPolicy,
                handler = { item, state -> state.handler(item) },
            )
        }
    }

    /**
     * Triggers every time the state machine enters this state. The passed [Flow] created by
     * [flowBuilder] will be collected and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [executionPolicy] is used to determine the behavior when a new emission from `Flow` arrives
     * before the previous [handler] invocation completed. By default [ExecutionPolicy.ORDERED]
     * is applied.
     */
    public fun <T> collectWhileInState(
        flowBuilder: ReadOnlyState<InputState>.() -> Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.ORDERED,
        handler: suspend State<InputState>.(item: T) -> ChangedState<S>,
    ) {
        sideEffectBuilders += SideEffectBuilder(isInState) { initialState ->
            CollectWhile(
                isInState = sideEffectIsInState(initialState),
                flow = ReadOnlyState(initialState).flowBuilder(),
                executionPolicy = executionPolicy,
                handler = { item, state -> state.handler(item) },
            )
        }
    }

    /**
     * An effect is a way to do some work without changing the state.
     * A typical use case is to trigger navigation as some sort of side effect or
     * triggering analytics or do logging.
     *
     * This is the "effect counterpart" of [collectWhileInState] and follows the same logic
     * when it triggers and when it gets canceled.
     */
    public fun <T> collectWhileInStateEffect(
        flow: Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.ORDERED,
        handler: suspend ReadOnlyState<InputState>.(item: T) -> Unit,
    ) {
        collectWhileInState(
            flow = flow,
            executionPolicy = executionPolicy,
            handler = {
                handler(it)
                NoStateChange
            },
        )
    }

    /**
     * An effect is a way to do some work without changing the state.
     * A typical use case is to trigger navigation as some sort of side effect or
     * triggering analytics or do logging.
     *
     * This is the "effect counterpart" of [collectWhileInState] and follows the same logic
     * when it triggers and when it gets canceled.
     */
    public fun <T> collectWhileInStateEffect(
        flowBuilder: ReadOnlyState<InputState>.() -> Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.ORDERED,
        handler: suspend ReadOnlyState<InputState>.(item: T) -> Unit,
    ) {
        collectWhileInState(
            flowBuilder = flowBuilder,
            executionPolicy = executionPolicy,
            handler = {
                handler(it)
                NoStateChange
            },
        )
    }
}
