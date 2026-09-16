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
}
