package br.com.eduardoamaral.desafiointelbras.ui.common

import androidx.annotation.StringRes
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.ValidationError

@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.error_network
    AppError.Timeout -> R.string.error_timeout
    AppError.Unauthorized -> R.string.error_unauthorized
    AppError.NotFound -> R.string.error_not_found
    AppError.Conflict -> R.string.error_conflict
    is AppError.InvalidData -> R.string.error_invalid_data
    is AppError.Server -> R.string.error_server
    AppError.Unknown -> R.string.error_unknown
}

/** Mensagem de Snackbar para um erro. Se o servidor explicou o motivo, ele aparece para o usuário. */
fun AppError.toUserMessage(): UserMessage {
    val serverMessage = (this as? AppError.InvalidData)?.serverMessage
    return if (serverMessage != null) {
        UserMessage(R.string.error_invalid_data_detail, isError = true, formatArgs = listOf(serverMessage))
    } else {
        UserMessage(messageRes(), isError = true)
    }
}

@StringRes
fun ValidationError.messageRes(): Int = when (this) {
    ValidationError.REQUIRED -> R.string.form_error_required
    ValidationError.TOO_LONG -> R.string.form_error_too_long
    ValidationError.INVALID_MAC -> R.string.form_error_invalid_mac
    ValidationError.INVALID_ALARM_PASSWORD -> R.string.form_error_invalid_alarm_password
    ValidationError.ALREADY_EXISTS -> R.string.form_error_already_exists
}
