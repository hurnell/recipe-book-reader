package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.graphics.Typeface

import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import apk.hurnell.recipebookreader.R
import androidx.core.widget.addTextChangedListener
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.Category
import com.google.android.material.textfield.TextInputLayout
import apk.hurnell.recipebookreader.databinding.ViewEditableCategoryBinding

class EditableCategoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), EditableView {

    private var _binding: ViewEditableCategoryBinding? = null
    private val binding get() = _binding!!
    private var categories = mutableListOf<Category>()
    private var selectedCategoryId: Int? = null
    private var originalText: String? = null
    private var hasLabel = false
    private var repository: PdfRepository? = null
    var onEditingChanged: ((view: Any, isEditing: Boolean) -> Unit)? = null
    private var currentBookId: Long = -1L

    private var isEditing = false
    private var isAddOnlyMode = false
    var onAccept: ((Long, String, Long?) -> Unit)? = null

    init {
        val inflater: LayoutInflater = LayoutInflater.from(context)
        _binding = ViewEditableCategoryBinding.inflate(inflater, this, true)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        binding.editableCategoryViewLabel.visibility = GONE

        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.EditableCategoryView)
            try {

                val defaultSizePx = resources.getDimension(R.dimen.default_category_text_size)
                val sizeInPx = ta.getDimension(
                    R.styleable.EditableCategoryView_categoryTextSize,
                    defaultSizePx
                )

                binding.editableCategoryViewText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizeInPx)
                binding.categoryChooser.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizeInPx)

                if (ta.getBoolean(R.styleable.EditableCategoryView_categoryBold, false)) {
                    binding.editableCategoryViewText.setTypeface(binding.editableCategoryViewText.typeface, Typeface.BOLD)
                    binding.categoryChooser.setTypeface(binding.categoryChooser.typeface, Typeface.BOLD)
                }
            } finally {
                ta.recycle()
            }
        }

        binding.categoryEditButton.setOnClickListener {
            toggleEditMode()
        }
        binding.categoryCancelButton.setOnClickListener { cancelEdit() }
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

        binding.categoryChooser.setAdapter(adapter)

        binding.categoryChooser.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId = categories[position].id.toInt()
        }
        binding.categoryChooser.addTextChangedListener { text ->
            if (text.isNullOrEmpty()) {
                binding.categoryChooserWrapper.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
            } else {
                binding.categoryChooserWrapper.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            }
        }
    }

    private fun toggleEditMode(forceClosed: Boolean = false) {
        if (forceClosed || isEditing) {
            isEditing = false
            onEditingChanged?.invoke(this, false)
            if (hasLabel) {
                binding.editableCategoryViewLabel.visibility = VISIBLE
            }
            binding.editableCategoryViewText.text = binding.categoryChooser.text
            binding.editableCategoryViewText.visibility = VISIBLE
            binding.categoryChooserWrapper.visibility = GONE
            binding.categoryEditButton.setImageResource(R.drawable.ic_edit)
            toggleEditButton(true)
            binding.categoryCancelButton.visibility = GONE
            val newText = binding.categoryChooser.text.toString()
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

            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.categoryChooser.windowToken, 0)
        } else {
            isEditing = true
            onEditingChanged?.invoke(this, true)
            binding.categoryChooserWrapper.visibility = VISIBLE
            binding.categoryChooser.setText(binding.editableCategoryViewText.text)
            binding.editableCategoryViewText.visibility = GONE
            if (hasLabel) {
                binding.editableCategoryViewLabel.visibility = VISIBLE
            }
            binding.categoryEditButton.setImageResource(R.drawable.ic_save)
            toggleEditButton(true)
            binding.categoryCancelButton.visibility = VISIBLE
            binding.categoryChooser.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.categoryChooser, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun setNewCategoryId(newCategoryId: Int){
        repository?.let { setCategories(it.loadCategories()) }
        selectedCategoryId = newCategoryId
    }

    private fun cancelEdit() {
        isEditing = false
        onEditingChanged?.invoke(this, false)
        binding.categoryChooserWrapper.visibility = GONE
        if (hasLabel) {
            binding.editableCategoryViewText.visibility = VISIBLE
        }
        binding.categoryEditButton.setImageResource(R.drawable.ic_edit)
        toggleEditButton(true)
        binding.categoryCancelButton.visibility = GONE
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.categoryChooser.windowToken, 0)
    }

    fun setText(value: String) {
        binding.editableCategoryViewText.text = value
        originalText = value
    }

    fun setLabel(value: String) {
        binding.editableCategoryViewLabel.text = value
        binding.editableCategoryViewLabel.visibility = VISIBLE
        hasLabel = true
        binding.categoryChooserWrapper.hint = "Choose (or create) $value "
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

        binding.editableCategoryViewText.setTypeface(binding.editableCategoryViewText.typeface, Typeface.NORMAL)

    }

    override fun toggleEditButton(show: Boolean) {
        val visible = show && (!isAddOnlyMode || isEditing)
        binding.categoryEditButton.visibility = if (visible) VISIBLE else GONE
    }

    fun setAddOnlyMode(enabled: Boolean) {
        isAddOnlyMode = enabled
        toggleEditButton(true)
    }

    fun beginEditing() {
        if (!isEditing) {
            toggleEditMode()
        }
    }
}