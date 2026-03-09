package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputFilter
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import apk.hurnell.recipebookreader.R
import androidx.core.content.withStyledAttributes
import apk.hurnell.recipebookreader.databinding.ViewEditableTextBinding

class EditableTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), EditableView {
    private var _binding: ViewEditableTextBinding? = null
    private val binding get() = _binding!!
    private var hasLabel = false
    private var originalText: String? = null
    private var lowercaseChars: String = ""

    private var isEditing = false
    var onEditingChanged: ((view: Any, isEditing: Boolean) -> Unit)? = null
    var onAccept: ((Long, String) -> Unit)? = null
    private var currentBookId: Long = -1L

    init {
        val inflater: LayoutInflater = LayoutInflater.from(context)
        _binding = ViewEditableTextBinding.inflate(inflater, this, true)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        lowercaseChars = context.getString(R.string.lowercase_chars)
        binding.editableTextViewLabel.visibility = GONE

        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.EditableTextView)
            try {
                val defaultSizePx = resources.getDimension(R.dimen.default_category_text_size)

                val sizeInPx = ta.getDimension(R.styleable.EditableTextView_textSize, defaultSizePx)
                context.withStyledAttributes(it, R.styleable.EditableTextView) {
                    binding.editableTextViewText.textSize =
                        getDimension(R.styleable.EditableTextView_textSize, defaultSizePx)
                    binding.editableTextViewText.setTextSize(
                        android.util.TypedValue.COMPLEX_UNIT_PX,
                        sizeInPx
                    )
                    binding.editableTextViewEditView.setTextSize(
                        android.util.TypedValue.COMPLEX_UNIT_PX,
                        sizeInPx
                    )
                    if (ta.getBoolean(R.styleable.EditableTextView_bold, false)) {
                        binding.editableTextViewText.setTypeface(
                            binding.editableTextViewText.typeface,
                            Typeface.BOLD
                        )
                        binding.editableTextViewEditView.setTypeface(
                            binding.editableTextViewEditView.typeface,
                            Typeface.BOLD
                        )
                    }
                }
            } finally {
                ta.recycle()
            }
        }

        binding.editButton.setOnClickListener {
            toggleEditMode()
        }
        binding.cancelButton.setOnClickListener { cancelEdit() }
    }

    fun onAccept(listener: (Long, String) -> Unit): EditableTextView {
        this.onAccept = listener
        return this
    }

    private fun toggleEditMode(forceClosed: Boolean = false) {
        if (forceClosed || isEditing) {
            isEditing = false
            onEditingChanged?.invoke(this, false)
            if (hasLabel) {
                binding.editableTextViewLabel.visibility = VISIBLE
            }
            binding.editableTextViewText.text = binding.editableTextViewEditView.text
            binding.editableTextViewText.visibility = VISIBLE
            binding.textChooserWrapper.visibility = GONE
            binding.editButton.setImageResource(R.drawable.ic_edit)
            binding.cancelButton.visibility = GONE
            val newText = binding.editableTextViewText.text.toString()
            if (newText != originalText && !forceClosed) {
                originalText = newText
                onAccept?.invoke(currentBookId, newText)
            }
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.editableTextViewEditView.windowToken, 0)
        } else {
            isEditing = true
            onEditingChanged?.invoke(this, true)
            binding.textChooserWrapper.visibility = VISIBLE
            binding.editableTextViewEditView.setText(binding.editableTextViewText.text)
            binding.editableTextViewEditView.visibility = VISIBLE
            binding.editableTextViewText.visibility = GONE
            if (hasLabel) {
                binding.editableTextViewLabel.visibility = VISIBLE
            }
            binding.editButton.setImageResource(R.drawable.ic_save)
            binding.cancelButton.visibility = VISIBLE
            binding.editableTextViewEditView.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.editableTextViewEditView, InputMethodManager.SHOW_IMPLICIT)

        }
    }

    private fun cancelEdit() {
        isEditing = false
        onEditingChanged?.invoke(this, false)
        binding.editableTextViewEditView.visibility = GONE
        if (hasLabel) {
            binding.editableTextViewText.visibility = VISIBLE
        }
        binding.editButton.setImageResource(R.drawable.ic_edit)
        binding.cancelButton.visibility = GONE
        binding.textChooserWrapper.visibility = GONE
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editableTextViewEditView.windowToken, 0)
    }

    fun setText(value: String) {
        binding.editableTextViewText.text = value
        originalText = value
    }

    fun setLabel(value: String) {
        binding.editableTextViewLabel.text = value
        binding.editableTextViewLabel.visibility = VISIBLE
        hasLabel = true
        binding.textChooserWrapper.hint = "Choose (or create) $value "
    }

    fun setParams(
        bookId: Long,
        text: String,
        label: String? = null,
        style: Int = Typeface.NORMAL,
        lowercase: Boolean? = false
    ) {
        currentBookId = bookId
        setText(text)
        binding.editableTextViewText.setTypeface(binding.editableTextViewText.typeface, style)
        if (label != null) {
            setLabel(label)
        }
        if (lowercase == true) {
            setAllowedChars()
        }
    }

    fun setParams(
        bookId: Long,
        text: String,
        label: String? = null,
        lowercase: Boolean? = false
    ) {
        toggleEditMode(true)
        currentBookId = bookId
        setText(text)
        binding.editableTextViewText.setTypeface(
            binding.editableTextViewText.typeface,
            Typeface.NORMAL
        )
        if (label != null) {
            setLabel(label)
        }
        if (lowercase == true) {
            setAllowedChars()
        }
    }

    private fun setAllowedChars() {
        binding.editableTextViewEditView.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            source?.map {
                val c = it.lowercaseChar()
                if (c in lowercaseChars) c else null
            }?.filterNotNull()?.joinToString("")
        })
    }

    override fun toggleEditButton(show: Boolean) {
        binding.editButton.visibility = if (show) VISIBLE else GONE
    }

}