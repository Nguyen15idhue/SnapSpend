using System.Security.Claims;
using SnapSpend.Api.Services;

namespace SnapSpend.Api.Endpoints;

/// <summary>
/// Endpoint AI dùng mini model local (Ollama); lỗi thì fallback luật, không 500.
/// </summary>
public static class AiEndpoints
{
    public static IEndpointRouteBuilder MapAi(this IEndpointRouteBuilder app)
    {
        var g = app.MapGroup("/api/ai").RequireAuthorization();

        // Cấu trúc hóa text OCR thành JSON {merchant, date, items, total}.
        g.MapPost("/extract", async (ExtractRequest req, ClaimsPrincipal p, OllamaService ollama) => {
            _ = p;
            if (string.IsNullOrWhiteSpace(req.Text)) return Results.BadRequest(new { message = "Text is required." });
            var result = await ollama.StructureReceiptAsync(req.Text);
            if (result is not null) return Results.Ok(result);
            return Results.Ok(new OllamaService.ReceiptExtract(null, null, [], null, true));
        });

        return app;
    }

    public record ExtractRequest(string Text);
}
