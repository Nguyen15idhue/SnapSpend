using System.ComponentModel.DataAnnotations;

namespace SnapSpend.Api.Models;

public class User
{
    public long Id { get; set; }
    [MaxLength(120)] public string Email { get; set; } = "";
    [MaxLength(40)] public string Username { get; set; } = "";
    [MaxLength(500)] public string PasswordHash { get; set; } = "";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public ICollection<Expense> Expenses { get; set; } = new List<Expense>();
}

public class Category
{
    public string Key { get; set; } = "";
    public string Name { get; set; } = "";
    public string Emoji { get; set; } = "";
}

public class Expense
{
    public long Id { get; set; }
    public long UserId { get; set; }
    public User? User { get; set; }
    public long Amount { get; set; }
    public string Category { get; set; } = "other";
    public string? ImageUrl { get; set; }
    public string? Note { get; set; }
    public DateOnly ExpenseDate { get; set; }
    public double? AiConfidence { get; set; }
    public string CategorySource { get; set; } = "user";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}

public class Friendship
{
    public long Id { get; set; }
    public long UserId { get; set; }
    public long FriendId { get; set; }
    public string Status { get; set; } = "pending";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

public class ExpenseShare
{
    public long Id { get; set; }
    public long ExpenseId { get; set; }
    public long OwnerId { get; set; }
    public long ReceiverId { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
