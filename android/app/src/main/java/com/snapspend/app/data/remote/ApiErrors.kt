package com.snapspend.app.data.remote

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Chuyển lỗi mạng/HTTP thành thông báo tiếng Việt thống nhất cho mọi màn hình.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException -> "Không kết nối được server. Kiểm tra mạng và thử lại."
    is SocketTimeoutException -> "Server phản hồi chậm. Vui lòng thử lại."
    is HttpException -> when (code()) {
        400 -> "Dữ liệu gửi lên không hợp lệ."
        401 -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        403 -> "Bạn không có quyền thực hiện thao tác này."
        404 -> "Không tìm thấy dữ liệu."
        409 -> "Dữ liệu đã tồn tại."
        in 500..599 -> "Server đang lỗi. Vui lòng thử lại sau."
        else -> "Yêu cầu thất bại (${code()})."
    }
    is IOException -> "Lỗi kết nối mạng. Vui lòng thử lại."
    else -> message ?: "Đã xảy ra lỗi. Vui lòng thử lại."
}
