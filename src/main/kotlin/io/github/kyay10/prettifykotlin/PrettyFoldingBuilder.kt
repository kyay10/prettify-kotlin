package io.github.kyay10.prettifykotlin

import arrow.core.raise.impure
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.isMultiline
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

val PACKAGE_FQNAME = FqName("io.github.kyay10.prettifykotlin")
val PRETTY = ClassId(PACKAGE_FQNAME, Name.identifier("Pretty"))
val PRETTY_NAME = Name.identifier("name")
val PRETTY_INFIX_ONLY = Name.identifier("infixOnly")
val PREFIX = ClassId(PACKAGE_FQNAME, Name.identifier("Prefix"))
val PREFIX_PREFIX = Name.identifier("prefix")
val PREFIX_SUFFIX = Name.identifier("suffix")
val POSTFIX = ClassId(PACKAGE_FQNAME, Name.identifier("Postfix"))
val POSTFIX_SUFFIX = Name.identifier("suffix")
val AUTOLAMBDA = ClassId(PACKAGE_FQNAME, Name.identifier("AutoLambda"))
val AUTOLAMBDA_PREFIX = Name.identifier("prefix")
val AUTOLAMBDA_ARROW = Name.identifier("arrow")
val AUTOLAMBDA_SUFFIX = Name.identifier("suffix")

class PrettyFoldingBuilder : FoldingBuilderEx() {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    if (quick) emptyArray()
    else buildList {
      root.accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) = impure {
          super.visitSimpleNameExpression(expression)

          analyze(expression) {
            val reference = expression.mainReference.resolveToSymbol()
            ensure(reference is KaAnnotated)
            val annotation = reference.annotations[PRETTY].singleOrNull().bind()
            val name = annotation.findConstantArgument(PRETTY_NAME)
            val infixOnly = annotation.findConstantArgument(PRETTY_INFIX_ONLY) { false }
            ensure(name is String)
            if (infixOnly) {
              ensure(expression.parent is KtBinaryExpression)
              ensure(expression is KtOperationReferenceExpression)
            }

            add(prettyFoldingDescriptor(expression.getReferencedNameElement(), name, dependencies = setOf(reference.psi)))
          }
        }

        override fun visitCallExpression(expression: KtCallExpression) = impure {
          super.visitCallExpression(expression)
          analyze(expression) {
            val reference = expression.calleeExpression.bind().mainReference.bind().resolveToSymbol()
            ensure(reference is KaAnnotated)
            impure {
              val annotation = reference.annotations[PREFIX].singleOrNull().bind()
              val prefix = annotation.findConstantArgument(PREFIX_PREFIX) { "" }
              val suffix = annotation.findConstantArgument(PREFIX_SUFFIX) { "" }

              val (leftPar, rightPar) = expression.valueArgumentList.bind().run {
                leftParenthesis.bind() to rightParenthesis.bind()
              }
              add(prettyFoldingDescriptor(leftPar, prefix, dependencies = setOf(reference.psi)))
              add(prettyFoldingDescriptor(rightPar, suffix, dependencies = setOf(reference.psi)))
            }
            impure {
              val call = expression.resolveToCall()?.successfulFunctionCallOrNull().bind()
              val reference = call.symbol
              call.argumentMapping.forEach { (arg, param) ->
                val annotation = param.symbol.annotations[AUTOLAMBDA].singleOrNull() ?: return@forEach
                val prefix = annotation.findConstantArgument(AUTOLAMBDA_PREFIX) { "" }
                val arrow = annotation.findConstantArgument(AUTOLAMBDA_ARROW) { "->" }
                val suffix = annotation.findConstantArgument(AUTOLAMBDA_SUFFIX) { "" }
                if (arg !is KtLambdaExpression) return@forEach
                val lambda = arg.functionLiteral
                add(prettyFoldingDescriptor(lambda.lBrace, prefix, dependencies = setOf(reference.psi)))
                lambda.lBrace.foldForSingleSpaceAfter?.let(::add)
                lambda.arrow?.let {
                  add(prettyFoldingDescriptor(it, arrow, dependencies = setOf(reference.psi)))
                  it.foldForSingleSpaceAfter?.let(::add)
                  it.foldForSingleSpaceBefore?.let(::add)
                }
                lambda.rBrace?.let {
                  add(prettyFoldingDescriptor(it, suffix, dependencies = setOf(reference.psi)))
                  if (!lambda.isMultiline()) it.foldForSingleSpaceBefore?.let(::add)
                }
              }
            }
          }
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) = impure {
          super.visitDotQualifiedExpression(expression)
          analyze(expression) {
            val selector = expression.selectorExpression.bind()
            val reference = selector.reference().bind().resolveToSymbol()
            ensure(reference is KaAnnotated)
            val annotation = reference.annotations[POSTFIX].singleOrNull().bind()
            val suffix = annotation.findConstantArgument(POSTFIX_SUFFIX) { "" }
            add(prettyFoldingDescriptor(expression.operationTokenNode.psi, suffix, dependencies = setOf(reference.psi)))
          }
        }

      })
    }.filterNotNull().toTypedArray()


  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}

private fun KaAnnotation.findConstantArgument(name: Name): Any? = findConstantArgument(name) { null }

private inline fun <reified T> KaAnnotation.findConstantArgument(name: Name, default: () -> T): T {
  val constValue = (arguments.find { it.name == name }?.expression as? KaAnnotationValue.ConstantValue)?.value
  val value = constValue?.value
  if (constValue == null || value !is T) return default()
  return value
}

private val PsiElement.foldForSingleSpaceBefore: FoldingDescriptor?
  get() = prevSibling.takeIf { it.text.endsWith(" ") }?.let {
    prettyFoldingDescriptor(it, "", range = TextRange(it.textRange.endOffset - 1, it.textRange.endOffset))
  }

private val PsiElement.foldForSingleSpaceAfter: FoldingDescriptor?
  get() = nextSibling.takeIf { it.text.startsWith(" ") }?.let {
    prettyFoldingDescriptor(it, "", range = TextRange(it.textRange.startOffset, it.textRange.startOffset + 1))
  }