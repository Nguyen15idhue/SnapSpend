using Microsoft.EntityFrameworkCore;
using SnapSpend.Api.Models;

namespace SnapSpend.Api.Data;

public class AppDbContext(DbContextOptions<AppDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>();
    public DbSet<Expense> Expenses => Set<Expense>();
    public DbSet<Category> Categories => Set<Category>();
    public DbSet<Friendship> Friendships => Set<Friendship>();
    public DbSet<ExpenseShare> ExpenseShares => Set<ExpenseShare>();
    public DbSet<TotalKeyword> TotalKeywords => Set<TotalKeyword>();
    public DbSet<NoisePattern> NoisePatterns => Set<NoisePattern>();
    public DbSet<CategoryKeyword> CategoryKeywords => Set<CategoryKeyword>();
    public DbSet<CategoryAlias> CategoryAliases => Set<CategoryAlias>();
    public DbSet<BoilerplatePattern> BoilerplatePatterns => Set<BoilerplatePattern>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<User>().ToTable("users");
        b.Entity<Expense>().ToTable("expenses");
        b.Entity<Category>().ToTable("categories");
        b.Entity<Friendship>().ToTable("friendships");
        b.Entity<ExpenseShare>().ToTable("expense_shares");
        b.Entity<User>().HasIndex(x => x.Email).IsUnique();
        b.Entity<User>().HasIndex(x => x.Username).IsUnique();
        b.Entity<Category>().HasKey(x => x.Key);
        b.Entity<Expense>().HasIndex(x => new { x.UserId, x.ExpenseDate });
        b.Entity<Friendship>().HasIndex(x => new { x.UserId, x.FriendId }).IsUnique();
        b.Entity<ExpenseShare>().HasIndex(x => new { x.ExpenseId, x.ReceiverId }).IsUnique();
        b.Entity<Expense>().Property(x => x.ExpenseDate).HasColumnType("date");
        b.Entity<Expense>().Property(x => x.Amount).HasColumnType("bigint");

        // Seed 9 category vào migration: nguồn schema duy nhất, idempotent.
        b.Entity<Category>().HasData(CategoryCatalog.AsEntities());

        // Seed engine nhận diện: thêm case mới = thêm row + migration mới (không release app).
        b.Entity<TotalKeyword>().ToTable("total_keywords");
        b.Entity<NoisePattern>().ToTable("noise_patterns");
        b.Entity<CategoryKeyword>().ToTable("category_keywords");
        b.Entity<CategoryAlias>().ToTable("category_aliases");
        b.Entity<BoilerplatePattern>().ToTable("boilerplate_patterns");
        b.Entity<TotalKeyword>().HasData(RecognitionSeed.TotalKeywords());
        b.Entity<NoisePattern>().HasData(RecognitionSeed.NoisePatterns());
        b.Entity<CategoryKeyword>().HasData(RecognitionSeed.CategoryKeywords());
        b.Entity<CategoryAlias>().HasData(RecognitionSeed.CategoryAliases());
        b.Entity<BoilerplatePattern>().HasData(RecognitionSeed.BoilerplatePatterns());
    }
}
