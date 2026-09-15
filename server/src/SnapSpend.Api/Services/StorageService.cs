using Microsoft.AspNetCore.Http;

namespace SnapSpend.Api.Services;

public class StorageService(IWebHostEnvironment env, IConfiguration config)
{
    private string UploadsRoot => Path.Combine(env.WebRootPath ?? Path.Combine(env.ContentRootPath, "wwwroot"), "uploads");

    public async Task<string?> SaveAsync(IFormFile? image)
    {
        if (image is null || image.Length == 0) return null;
        if (image.Length > 10 * 1024 * 1024) throw new InvalidOperationException("Image too large. Max 10 MB.");
        var allowed = new[] { "image/jpeg", "image/png", "image/webp" };
        if (!allowed.Contains(image.ContentType, StringComparer.OrdinalIgnoreCase)) throw new InvalidOperationException("Unsupported image type.");
        Directory.CreateDirectory(UploadsRoot);
        var ext = Path.GetExtension(image.FileName);
        if (string.IsNullOrWhiteSpace(ext)) ext = ".jpg";
        var name = $"{Guid.NewGuid():N}{ext.ToLowerInvariant()}";
        await using var stream = File.Create(Path.Combine(UploadsRoot, name));
        await image.CopyToAsync(stream);
        var baseUrl = config["Storage:BaseUrl"]?.TrimEnd('/');
        return $"{baseUrl}/{name}";
    }

    /// <summary>Xóa file ảnh theo URL đã lưu. An toàn khi file/đường dẫn không hợp lệ.</summary>
    public void DeleteByUrl(string? url)
    {
        if (string.IsNullOrWhiteSpace(url)) return;
        try
        {
            var name = Path.GetFileName(new Uri(url).AbsolutePath);
            if (string.IsNullOrWhiteSpace(name)) return;
            var path = Path.Combine(UploadsRoot, name);
            if (File.Exists(path)) File.Delete(path);
        }
        catch { }
    }
}
