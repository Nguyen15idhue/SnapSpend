namespace SnapSpend.Api.Models;

/// <summary>
/// Từ khóa nhận diện dòng Tổng (scope Total) hoặc dòng loại trừ (scope Exclude: VAT/phụ thu...).
/// Keyword lưu dạng đã chuẩn hóa (thường + bỏ dấu) để match trực tiếp text đã normalize.
/// </summary>
public class TotalKeyword
{
    public int Id { get; set; }
    public string Keyword { get; set; } = "";
    public int Priority { get; set; }
    public bool Exclude { get; set; }
    public bool IsActive { get; set; } = true;
}

/// <summary>Mẫu dòng nhiễu (MST, mã CQT, số hóa đơn, ngày...) — KHÔNG lấy số tiền từ đây.</summary>
public class NoisePattern
{
    public int Id { get; set; }
    public string Pattern { get; set; } = "";
    public bool IsActive { get; set; } = true;
}

/// <summary>
/// Từ khóa phân loại: Source merchant (tên cửa hàng/thương hiệu, thắng khi hòa)
/// hoặc item (tên món lẻ). Weight = độ tin cậy khi từ khóa này quyết định.
/// </summary>
public class CategoryKeyword
{
    public int Id { get; set; }
    public string Category { get; set; } = "";
    public string Keyword { get; set; } = "";
    public double Weight { get; set; }
    public string Source { get; set; } = "item";
    public bool IsActive { get; set; } = true;
}

/// <summary>Ánh xạ nhãn/model trả về (kể cả tiếng Việt) về key danh mục chuẩn.</summary>
public class CategoryAlias
{
    public int Id { get; set; }
    public string Alias { get; set; } = "";
    public string Category { get; set; } = "";
    public bool IsActive { get; set; } = true;
}

/// <summary>Mẫu boilerplate (tiêu đề/thông tin hành chính) — bỏ khi tóm tắt nội dung.</summary>
public class BoilerplatePattern
{
    public int Id { get; set; }
    public string Pattern { get; set; } = "";
    public bool IsActive { get; set; } = true;
}
