/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

val IrDeclaration.isReal: Boolean get() = !isFakeOverride

val IrDeclaration.isFakeOverride: Boolean
    get() = when (this) {
        is IrSimpleFunction -> isFakeOverride
        is IrProperty -> isFakeOverride
        else -> false
    }

val IrSimpleFunction.target: IrSimpleFunction
    get() = if (modality == Modality.ABSTRACT)
        this
    else
        resolveFakeOverride() ?: error("Could not resolveFakeOverride() for ${this.render()}")

val IrFunction.target: IrFunction get() = when (this) {
    is IrSimpleFunction -> this.target
    is IrConstructor -> this
    else -> error(this)
}

fun IrSimpleFunction.collectRealOverrides(
    toSkip: (IrSimpleFunction) -> Boolean = { false },
    filter: (IrOverridableMember) -> Boolean = { false }
): Set<IrSimpleFunction> {
    if (isReal && !toSkip(this)) return setOf(this)

    @Suppress("UNCHECKED_CAST")
    return this.overriddenSymbols
        .map { it.owner }
        .collectAndFilterRealOverrides(
            {
                require(it is IrSimpleFunction) { "Expected IrSimpleFunction: ${it.render()}" }
                toSkip(it)
            },
            filter
        ) as Set<IrSimpleFunction>
}

fun Collection<IrOverridableMember>.collectAndFilterRealOverrides(
    toSkip: (IrOverridableMember) -> Boolean = { false },
    filter: (IrOverridableMember) -> Boolean = { false },
    getOverriddenPropertySymbols: IrProperty.() -> List<IrPropertySymbol> = {
        // TODO: use IrProperty.overriddenSymbols instead: KT-47019
        //  (at the moment it breaks K/N in at least :kotlin-native:backend.native:tests:coroutines_functionReference_eqeq_name)
        (getter ?: setter)?.overriddenSymbols?.mapNotNull { it.owner.correspondingPropertySymbol }.orEmpty()
    },
): Set<IrOverridableMember> {

    val visited = mutableSetOf<IrOverridableMember>()
    val realOverrides = mutableMapOf<Any, IrOverridableMember>()

    /*
        Due to IR copying in performByIrFile, overrides should only be distinguished up to their signatures.
     */
    fun IrOverridableMember.toKey(): Any = symbol.signature ?: this

    fun overriddenSymbols(declaration: IrOverridableMember) = when (declaration) {
        is IrSimpleFunction -> declaration.overriddenSymbols
        is IrProperty -> declaration.getOverriddenPropertySymbols()
        else -> error("Unexpected overridable member: ${declaration.render()}")
    }

    fun collectRealOverrides(member: IrOverridableMember) {
        if (!visited.add(member) || filter(member)) return

        if (member.isReal && !toSkip(member)) {
            realOverrides[member.toKey()] = member
        } else {
            overriddenSymbols(member).forEach { collectRealOverrides(it.owner as IrOverridableMember) }
        }
    }

    this.forEach { collectRealOverrides(it) }

    fun excludeRepeated(member: IrOverridableMember) {
        if (!visited.add(member)) return

        overriddenSymbols(member).forEach {
            val owner = it.owner as IrOverridableMember
            realOverrides.remove(owner.toKey())
            excludeRepeated(owner)
        }
    }

    visited.clear()
    realOverrides.toList().forEach { excludeRepeated(it.second) }

    return realOverrides.values.toSet()
}

// TODO: use this implementation instead of any other
fun IrSimpleFunction.resolveFakeOverride(allowAbstract: Boolean = false, toSkip: (IrSimpleFunction) -> Boolean = { false }): IrSimpleFunction? {
    return if (allowAbstract) {
        val reals = collectRealOverrides(toSkip)
        if (reals.isEmpty()) error("No real overrides for ${this.render()}")
        reals.first()
    } else {
        collectRealOverrides(toSkip, { it.modality == Modality.ABSTRACT })
            .let { realOverrides ->
                // Kotlin forbids conflicts between overrides, but they may trickle down from Java.
                realOverrides.singleOrNull { it.parent.safeAs<IrClass>()?.isInterface != true }
                // TODO: We take firstOrNull instead of singleOrNull here because of KT-36188.
                    ?: realOverrides.firstOrNull()
            }
    }
}
