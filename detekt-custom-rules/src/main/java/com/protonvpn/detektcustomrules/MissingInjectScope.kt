/*
 * Copyright (c) 2022. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.detektcustomrules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClass

private val INJECT_SCOPE_ANNOTATIONS = arrayOf("Singleton", "Distinct", "HiltViewModel", "Reusable")

class MissingInjectScope(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        "MissingInjectScope",
        Severity.CodeSmell,
        "This class has an @Inject constructor but no scope annotation.",
        Debt(0, 0, 1)
    )

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        super.visitPrimaryConstructor(constructor)
        val containingClass = constructor.containingClass()
        if (containingClass != null &&
            constructor.hasAnnotation("Inject") &&
            !containingClass.hasAnnotation(*INJECT_SCOPE_ANNOTATIONS)
        ) {
            val annotationsString = INJECT_SCOPE_ANNOTATIONS.joinToString(", ") { "@$it" }
            report(
                CodeSmell(
                    issue,
                    Entity.from(containingClass.nameIdentifier ?: constructor),
                    "Class ${containingClass.name} has an @Inject-annotated constructor but no scope annotation." +
                        " Add one of: $annotationsString"
                )
            )
        }
    }
}
