package com.shop.product;

import com.shop.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductController(ProductRepository products, CategoryRepository categories) {
        this.products = products; this.categories = categories;
    }

    public record ProductRequest(@NotBlank String name, Long categoryId, @NotBlank(message = "Ban chua nhap don vi cho san pham") String unit,
                                 @NotNull @DecimalMin("0") BigDecimal price) {}
    public record ProductDto(Long id, String name, Long categoryId, String categoryName, String unit, BigDecimal price) {}
    public record CategoryRequest(@NotBlank String name) {}

    private ProductDto dto(Product p) {
        Category c = p.getCategory();
        return new ProductDto(p.getId(), p.getName(), c == null ? null : c.getId(),
                c == null ? null : c.getName(), p.getUnit(), p.getPrice());
    }

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public List<ProductDto> list(@RequestParam(defaultValue = "") String q) {
        return products.findByActiveTrueAndNameContainingIgnoreCaseOrderByName(q).stream().map(this::dto).toList();
    }

    @PostMapping("/products")
    @Transactional
    public ProductDto create(@Valid @RequestBody ProductRequest r) {
        Product p = new Product();
        apply(p, r);
        return dto(products.save(p));
    }

    @PutMapping("/products/{id}")
    @Transactional
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest r) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay san pham"));
        apply(p, r);
        return dto(p);
    }

    /** Xoa mem: giu lai de hoa don cu van tham chieu duoc. */
    @DeleteMapping("/products/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        products.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay san pham")).setActive(false);
    }

    private void apply(Product p, ProductRequest r) {
        p.setName(r.name().trim());
        p.setUnit(r.unit());
        p.setPrice(r.price());
        p.setCategory(r.categoryId() == null ? null : categories.findById(r.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Loai hang khong ton tai")));
    }

    @GetMapping("/categories")
    public List<Category> categories() { return categories.findAll(); }

    @PutMapping("/categories/{id}")
    @Transactional
    public Category updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay loai hang"));
        c.setName(r.name().trim());
        return c;
    }

    @DeleteMapping("/categories/{id}")
    @Transactional
    public void deleteCategory(@PathVariable Long id) {
        if (products.existsByCategoryIdAndActiveTrue(id))
            throw ApiException.conflict("Loai hang dang co san pham, hay chuyen san pham sang loai khac truoc");
        products.clearCategory(id); // san pham da xoa mem van giu tham chieu -> bo tham chieu de xoa duoc
        categories.deleteById(id);
    }

    @PostMapping("/categories")
    public Category createCategory(@Valid @RequestBody CategoryRequest r) {
        Category c = new Category();
        c.setName(r.name().trim());
        return categories.save(c);
    }
}
