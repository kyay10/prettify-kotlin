package io.github.kyay10.prettifykotlin

import arrow.core.raise.impure
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import io.github.kyay10.prettifykotlin.Settings.Companion.settings
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
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
import java.util.WeakHashMap

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
  private val listener = object : BulkAwareDocumentListener.Simple {
    val effectMap = WeakHashMap<Document, MutableSet<Document>>()
    override fun documentChanged(event: DocumentEvent) {
      effectMap[event.document]?.forEach {
        EditorFactory.getInstance().getEditors(it).forEach(Editor::updateFoldings)
      }
    }
  }

  @Suppress("SimpleRedundantLet") //KTIJ-36603
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean) = with(document) {
    val project = root.project
    val settings = project.settings
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    fun PsiElement.addListenerIfNecessary() {
      val file = this.containingFile ?: return
      psiDocumentManager.getCachedDocument(file)?.let {
        listener.effectMap.getOrPut(it) {
          it.addDocumentListener(listener, object: Disposable.Default {})
          mutableSetOf()
        }.add(document)
      }
    }
    if (quick) emptyArray()
    else buildList {
      root.accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) = impure {
          super.visitSimpleNameExpression(expression)

          analyze(expression) {
            val reference = expression.mainReference.resolveToSymbol()
            ensure(reference is KaCallableSymbol)
            reference.psi?.addListenerIfNecessary()
            val (name, infixOnly) = settings.fqNameToPrettyData[reference.fqName]?.let { it.pretty.bind() } ?: run {
              val annotation = reference.annotations[PRETTY].singleOrNull().bind()
              val name: String = annotation.findConstantArgument(PRETTY_NAME)
              val infixOnly = annotation.findConstantArgument(PRETTY_INFIX_ONLY) { false }
              PrettyData(name, infixOnly)
            }
            if (infixOnly) {
              ensure(expression.parent is KtBinaryExpression)
              ensure(expression is KtOperationReferenceExpression)
            }

            add(
              prettyFoldingDescriptor(
                expression.getReferencedNameElement(),
                name,
                dependencies = setOfNotNull(reference.psi)
              )
            )
          }
        }

        override fun visitCallExpression(expression: KtCallExpression) = impure {
          super.visitCallExpression(expression)
          analyze(expression) {
            val reference = expression.calleeExpression.bind().mainReference.bind().resolveToSymbol()
            ensure(reference is KaCallableSymbol)
            reference.psi?.addListenerIfNecessary()
            impure {
              val (prefix, suffix) = settings.fqNameToPrettyData[reference.fqName]?.let { it.prefix.bind() } ?: run {
                val annotation = reference.annotations[PREFIX].singleOrNull().bind()
                val prefix = annotation.findConstantArgument(PREFIX_PREFIX) { "" }
                val suffix = annotation.findConstantArgument(PREFIX_SUFFIX) { "" }
                PrefixData(prefix, suffix)
              }

              val (leftPar, rightPar) = expression.valueArgumentList.bind().run {
                leftParenthesis.bind() to rightParenthesis.bind()
              }
              add(prettyFoldingDescriptor(leftPar, prefix, dependencies = setOfNotNull(reference.psi)))
              add(prettyFoldingDescriptor(rightPar, suffix, dependencies = setOfNotNull(reference.psi)))
            }
            impure {
              val call = expression.resolveToCall()?.successfulFunctionCallOrNull().bind()
              val reference = call.symbol
              reference.psi?.addListenerIfNecessary()
              call.argumentMapping.forEach { (arg, param) ->
                val annotation = param.symbol.annotations[AUTOLAMBDA].singleOrNull() ?: return@forEach
                val prefix = annotation.findConstantArgument(AUTOLAMBDA_PREFIX) { "" }
                val arrow = annotation.findConstantArgument(AUTOLAMBDA_ARROW) { "->" }
                val suffix = annotation.findConstantArgument(AUTOLAMBDA_SUFFIX) { "" }
                if (arg !is KtLambdaExpression) return@forEach
                val lambda = arg.functionLiteral
                add(prettyFoldingDescriptor(lambda.lBrace, prefix, dependencies = setOfNotNull(reference.psi)))
                lambda.lBrace.foldForSingleSpaceAfter?.let(::add)
                lambda.arrow?.let {
                  add(prettyFoldingDescriptor(it, arrow, dependencies = setOfNotNull(reference.psi)))
                  it.foldForSingleSpaceAfter?.let(::add)
                  it.foldForSingleSpaceBefore?.let(::add)
                }
                lambda.rBrace?.let {
                  add(prettyFoldingDescriptor(it, suffix, dependencies = setOfNotNull(reference.psi)))
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
            ensure(reference is KaCallableSymbol)
            reference.psi?.addListenerIfNecessary()
            val (suffix) = settings.fqNameToPrettyData[reference.fqName]?.let { it.postfix.bind() } ?: run {
              val annotation = reference.annotations[POSTFIX].singleOrNull().bind()
              PostfixData(annotation.findConstantArgument(POSTFIX_SUFFIX) { "" })
            }
            add(
              prettyFoldingDescriptor(
                expression.operationTokenNode.psi,
                suffix,
                dependencies = setOfNotNull(reference.psi)
              )
            )
          }
        }
      })
    }.filterNotNull().toTypedArray()
  }


  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}
