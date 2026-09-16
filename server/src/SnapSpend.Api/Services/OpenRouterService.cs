using System.Text;
using System.Text.Json;

namespace SnapSpend.Api.Services;

/// <summary>
/// Gọi OpenRouter (model free) để viết nhận xét phân tích. Lỗi/hết quota -> null để dùng bản luật.
/// </summary>
public class OpenRouterService(IConfiguration config, IHttpClientFactory httpClientFactory)
{
    private string? ApiKey => config["OpenRouter:ApiKey"];
    private string Model => config["OpenRouter:Model"] is { Length: > 0 } m ? m : "nvidia/nemotron-3.5-lightning:free";

    public async Task<string?> NarrateAnalysisAsync(string summary)
    {
        if (string.IsNullOrWhiteSpace(ApiKey)) return null;
        try
        {
            var prompt = "Bạn là trợ lý chi tiêu. Chỉ dùng số liệu sau, không bịa thêm số. "
                + "Viết 2-3 câu tiếng Việt nhận xét: tổng chi, nhóm chi nhiều nhất, điểm bất thường (nếu có).\nSố liệu:\n" + summary;
            var body = new
            {
                model = Model,
                messages = new object[] { new { role = "user", content = prompt } }
            };
            var client = httpClientFactory.CreateClient("openrouter");
            using var req = new HttpRequestMessage(HttpMethod.Post, "https://openrouter.ai/api/v1/chat/completions");
            req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", ApiKey);
            req.Content = new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json");
            using var res = await client.SendAsync(req);
            if (!res.IsSuccessStatusCode) return null;
            using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
            return doc.RootElement.TryGetProperty("choices", out var choices) && choices.GetArrayLength() > 0
                && choices[0].TryGetProperty("message", out var msg)
                && msg.TryGetProperty("content", out var content)
                ? content.GetString() : null;
        }
        catch { return null; }
    }

    /// <summary>
    /// Xác minh tổng tiền từ text OCR khi engine nội bộ bó tay (LOW/null).
    /// Null khi thiếu key/lỗi/quota — caller dùng trống + hint, không chặn luồng.
    /// </summary>
    public async Task<long?> VerifyTotalAsync(string text)
    {
        if (string.IsNullOrWhiteSpace(ApiKey) || string.IsNullOrWhiteSpace(text)) return null;
        try
        {
            var prompt = "Đọc văn bản hóa đơn tiếng Việt sau, tìm SỐ TIỀN THANH TOÁN CUỐI CÙNG "
                + "(dòng Tổng/Tổng cộng/Thanh toán; KHÔNG lấy VAT/phụ thu/tạm tính, KHÔNG lấy mã số/mã hóa đơn/số điện thoại). "
                + "Chỉ trả JSON: {\"total\": số nguyên VNĐ hoặc null}. Văn bản:\n" + text;
            var body = new
            {
                model = Model,
                messages = new object[] { new { role = "user", content = prompt } }
            };
            var client = httpClientFactory.CreateClient("openrouter");
            using var req = new HttpRequestMessage(HttpMethod.Post, "https://openrouter.ai/api/v1/chat/completions");
            req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", ApiKey);
            req.Content = new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json");
            using var res = await client.SendAsync(req);
            if (!res.IsSuccessStatusCode) return null;
            using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
            if (!doc.RootElement.TryGetProperty("choices", out var choices) || choices.GetArrayLength() == 0) return null;
            var content = choices[0].TryGetProperty("message", out var msg) && msg.TryGetProperty("content", out var c)
                ? c.GetString() : null;
            if (string.IsNullOrWhiteSpace(content)) return null;
            // Bóc JSON trong text (model có thể bọc ``` fences).
            var s = content.Trim();
            var start = s.IndexOf('{');
            var end = s.LastIndexOf('}');
            if (start < 0 || end <= start) return null;
            using var json = JsonDocument.Parse(s[start..(end + 1)]);
            return json.RootElement.TryGetProperty("total", out var t) && t.TryGetInt64(out var v) && v is >= 1000 and <= 9_999_999_999 ? v : null;
        }
        catch { return null; }
    }
}
