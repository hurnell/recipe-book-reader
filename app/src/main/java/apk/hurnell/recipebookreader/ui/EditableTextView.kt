package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputFilter
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import apk.hurnell.recipebookreader.R
import androidx.core.content.withStyledAttributes

class EditableTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), EditableView {

    private val textView: TextView
    private val labelView: TextView
    private val editText: EditText
    private val editButton: ImageButton
    private val cancelButton: ImageButton
    private var hasLabel = false
    private var originalText: String? = null
    private var lowercaseChars: String = ""

    private var isEditing = false
    var onEditingChanged: ((view: Any, isEditing: Boolean) -> Unit)? = null
    var onAccept: ((Long, String) -> Unit)? = null
    private var currentBookId: Long = -1L

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_editable_text, this, true)
        lowercaseChars = context.getString(R.string.lowercase_chars)
        textView = findViewById(R.id.editableTextViewText)
        labelView = findViewById(R.id.editableTextViewLabel)
        labelView.visibility = GONE
        editText = findViewById(R.id.editableTextViewEditView)
        editButton = findViewById(R.id.editButton)
        cancelButton = findViewById(R.id.cancelButton)

        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.EditableTextView)
            try {
                val defaultSizePx = resources.getDimension(R.dimen.default_category_text_size)

                val sizeInPx = ta.getDimension(R.styleable.EditableTextView_textSize, defaultSizePx)
                context.withStyledAttributes(it, R.styleable.EditableTextView) {
                    textView.textSize = getDimension(R.styleable.EditableTextView_textSize, defaultSizePx)
                    textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizeInPx)
                    editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizeInPx)
                    if (ta.getBoolean(R.styleable.EditableTextView_bold, false)) {
                        textView.setTypeface(textView.typeface, Typeface.BOLD)
                        editText.setTypeface(editText.typeface, Typeface.BOLD)
                    }
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

    fun onAccept(listener: (Long, String) -> Unit): EditableTextView {
        this.onAccept = listener
        return this
    }
    override fun toggleEditButton(show: Boolean) {
        editButton.visibility = if (show) VISIBLE else GONE
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
            editText.visibility = GONE
            editButton.setImageResource(R.drawable.ic_edit)
            cancelButton.visibility = GONE
            val newText = textView.text.toString()
            if (newText != originalText) {
                originalText = newText
                onAccept?.invoke(currentBookId, newText)
            }
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText.windowToken, 0)
        } else {
            isEditing = true
            onEditingChanged?.invoke(this, true)
            editText.setText(textView.text)
            editText.visibility = VISIBLE
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

    private fun cancelEdit() {
        isEditing = false
        onEditingChanged?.invoke(this, false)
        editText.visibility = GONE
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
        textView.setTypeface(textView.typeface, style)
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
        textView.setTypeface(textView.typeface, Typeface.NORMAL)
        if (label != null) {
            setLabel(label)
        }
        if (lowercase == true) {
            setAllowedChars()
        }
    }

    private fun setAllowedChars() {
        editText.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            source?.map {
                val c = it.lowercaseChar()
                if (c in lowercaseChars) c else null
            }?.filterNotNull()?.joinToString("")
        })
    }
}