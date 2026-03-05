package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.graphics.Typeface

import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import apk.hurnell.recipebookreader.R
import androidx.core.widget.addTextChangedListener
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.Category
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class EditableCategoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), EditableView {

    private val textView: TextView
    private val labelView: TextView
    private val editWrapper: TextInputLayout
    private val editText: MaterialAutoCompleteTextView
    private var categories = mutableListOf<Category>()
    private var selectedCategoryId: Int? = null
    private var originalText: String? = null
    private val editButton: ImageButton
    private val cancelButton: ImageButton
    private var hasLabel = false
    private var repository: PdfRepository? = null
    var onEditingChanged: ((view: Any, isEditing: Boolean) -> Unit)? = null
    private var currentBookId: Long = -1L

    private var isEditing = false
    var onAccept: ((Long, String, Long?) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_editable_category, this, true)
        labelView = findViewById(R.id.editableCategoryViewLabel)
        textView = findViewById(R.id.editableCategoryViewText)
        labelView.visibility = GONE
        editWrapper = findViewById(R.id.categoryChooserWrapper)
        editText = findViewById(R.id.categoryChooser)
        editButton = findViewById(R.id.categoryEditButton)
        cancelButton = findViewById(R.id.categoryCancelButton)

        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.EditableCategoryView)
            try {

                val defaultSizePx = resources.getDimension(R.dimen.default_category_text_size)
                val sizeInPx = ta.getDimension(
                    R.styleable.EditableCategoryView_categoryTextSize,
                    defaultSizePx
                )

                textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizeInPx)
                editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizeInPx)

                if (ta.getBoolean(R.styleable.EditableCategoryView_categoryBold, false)) {
                    textView.setTypeface(textView.typeface, Typeface.BOLD)
                    editText.setTypeface(editText.typeface, Typeface.BOLD)
                }
            } finally {
                ta.recycle()
            }
        }

        editButton.setOnClickListener {
            toggleEditMode()
        }
        cancelButton.setOnClickListener { cancelEdit() }
    }


    fun onAccept(listener: (Long, String, Long?) -> Unit): EditableCategoryView {
        this.onAccept = listener
        return this
    }

    fun setCategories(list: List<Category>) {
        categories.clear()
        categories.addAll(list)

        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            categories
        )

        editText.setAdapter(adapter)

        editText.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId = categories[position].id.toInt()
        }
        editText.addTextChangedListener { text ->
            if (text.isNullOrEmpty()) {
                editWrapper.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
            } else {
                editWrapper.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            }
        }
    }

    private fun toggleEditMode(forceClosed: Boolean = false) {
        if (forceClosed || isEditing) {
            isEditing = false
            onEditingChanged?.invoke(this, false)
            if (hasLabel) {
                labelView.visibility = VISIBLE
            }
            textView.text = editText.text
            textView.visibility = VISIBLE
            editWrapper.visibility = GONE
            editButton.setImageResource(R.drawable.ic_edit)
            cancelButton.visibility = GONE
            val newText = editText.text.toString()
            if (newText != originalText && !forceClosed) {
                val newCategory = categories.find { it.category.equals(newText, false) }
                originalText = newText
                if (newCategory != null) {
                    onAccept?.invoke(currentBookId, newCategory.category, newCategory.id)
                    selectedCategoryId = newCategory.id.toInt()
                } else {
                    onAccept?.invoke(currentBookId, newText, null)
                }
            }
            textView.text = newText

            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText.windowToken, 0)
        } else {
            isEditing = true
            onEditingChanged?.invoke(this, true)
            editWrapper.visibility = VISIBLE
            editText.setText(textView.text)
            textView.visibility = GONE
            if (hasLabel) {
                labelView.visibility = VISIBLE
            }
            editButton.setImageResource(R.drawable.ic_save)
            cancelButton.visibility = VISIBLE
            editText.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)

        }
    }

    fun setNewCategoryId(newCategoryId: Int){
        repository?.let { setCategories(it.loadCategories()) }
        selectedCategoryId = newCategoryId
    }

    private fun cancelEdit() {
        isEditing = false
        onEditingChanged?.invoke(this, false)
        editWrapper.visibility = GONE
        if (hasLabel) {
            textView.visibility = VISIBLE
        }
        editButton.setImageResource(R.drawable.ic_edit)
        cancelButton.visibility = GONE
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    fun setText(value: String) {
        textView.text = value
        originalText = value
    }

    fun setLabel(value: String) {
        labelView.text = value
        labelView.visibility = VISIBLE
        hasLabel = true
        editWrapper.hint = "Choose (or create) $value "
    }


    fun setParams(
        bookId: Long,
        currentRepository: PdfRepository,
        categoryId: Int?,
        label: String? = null
    ) {
        currentBookId = bookId
        if (label != null) {
            setLabel(label)
        }
        toggleEditMode(true)
        repository = currentRepository
        setCategories(currentRepository.loadCategories())
        selectedCategoryId = categoryId

        if (categoryId != null) {
            val category = categories.find { it.id.toInt() == categoryId }
            if (category != null) {
                setText(category.category)
            } else {
                setText("")
            }
        } else {
            setText("")
        }

        textView.setTypeface(textView.typeface, Typeface.NORMAL)

    }

    override fun toggleEditButton(show: Boolean) {
        editButton.visibility = if (show) VISIBLE else GONE
    }
}