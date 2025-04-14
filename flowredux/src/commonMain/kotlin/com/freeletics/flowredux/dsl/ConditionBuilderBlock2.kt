package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class ConditionBuilderBlock2<InputState : S, S : Any, A : Any> internal constructor(
    override val isInState: SideEffectBuilder.IsInState<S>,
) : BaseBuilderBlock2<InputState, S, A>() {
    /**
     * Anything inside this block will only run while the [identity] of the current state
     * remains the same. The `identity` is determined by the given function and uses
     * [equals] for the comparison.
     *
     * When the `identity` changes anything currently running in the block is cancelled.
     * Afterwards a the blocks are started again for the new `identity`.
     */
    public fun untilIdentityChanges(
        identity: (InputState) -> Any?,
        block: IdentityBuilderBlock2<InputState, S, A>.() -> Unit,
    ) {
        sideEffectBuilders += IdentityBuilderBlock2<InputState, S, A>(isInState, identity)
            .apply(block)
            .sideEffectBuilders
    }
}
