package io.github.kyay10.prettifykotlin

import arrow.core.raise.impure
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.inspections.AbstractRangeInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.ValueArgument

val PRETTY_FQNAME = FqName("io.github.kyay10.prettifykotlin.Pretty")
val PREFIX_FQNAME = FqName("io.github.kyay10.prettifykotlin.Prefix")

class PrettyFoldingBuilder : FoldingBuilderEx() {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    if (quick) emptyArray()
    else buildList {
      root.accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) = impure {
          super.visitSimpleNameExpression(expression)

          ensure(expression.parent !is KtThisExpression && expression.parent !is KtSuperExpression && expression !is KtLabelReferenceExpression)
          val reference = expression.mainReference.resolve()
          ensure(reference is KtAnnotated)

          val annotation = reference.findAnnotation(PRETTY_FQNAME).bind()
          val name = annotation.valueArguments.singleOrNull().bind().constValue
          ensure(name is String)

          add(PrettyFoldingDescriptor(expression, name))
        }

        override fun visitCallExpression(expression: KtCallExpression) = impure {
          super.visitCallExpression(expression)

          val reference = expression.calleeExpression.bind().mainReference.bind().resolve()
          ensure(reference is KtAnnotated)

          val annotation = reference.findAnnotation(PREFIX_FQNAME).bind()
          val (prefix, suffix) = annotation.valueArguments.also { ensure(it.size == 2) }.map { it.constValue }
          ensure(prefix is String && suffix is String)

          val group = FoldingGroup.newGroup("brackets")
          val (leftPar, rightPar) = expression.valueArgumentList.bind().run {
            leftParenthesis.bind() to rightParenthesis.bind()
          }
          add(PrettyFoldingDescriptor(leftPar, prefix, group, neverExpands = false))
          add(PrettyFoldingDescriptor(rightPar, suffix, group, neverExpands = false))
        }
      })
    }.toTypedArray()


  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}

private val ValueArgument.constValue get() = getArgumentExpression()?.constantValueOrNull()?.value