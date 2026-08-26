import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Product } from '../../models/product';
import { ProductService } from '../../services/product.service';
import { CommonModule } from '@angular/common';
import { UserService } from '../../services/user.service';
import { HttpClient } from '@angular/common/http';
@Component({
  selector: 'app-product-page',
  templateUrl: './product-page.component.html',
  standalone: true, // If standalone
  imports: [CommonModule, ReactiveFormsModule], // 2. Add here
})
export class ProductPageComponent implements OnInit {
  // Constant pointing to your Angular public/assets directory fallback
  readonly DEFAULT_NO_IMAGE = '/noimage.png';

  product: Product | null = null;
  form!: FormGroup;

  isLoading = true;
  editing = false;
  isSaving = false;
  isDeleting = false;
  showDeleteModal = false;
  isFullViewOpen = false;
  error: string | null = null;
  isOwner = false;

  // Active gallery states
  imagePreviews: string[] = [];
  selectedFiles: File[] = [];
  selectedImageIndex = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder,
    private productService: ProductService,
    private userService: UserService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.initForm();
    const productId = this.route.snapshot.paramMap.get('id');
    if (productId) {
      this.loadProduct(productId);
    } else {
      this.error = 'Invalid Product ID';
      this.isLoading = false;
    }
  }

  private initForm(): void {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
      price: [0, [Validators.required, Validators.min(0.01), Validators.max(9999999.99)]],
      quantity: [0, [Validators.required, Validators.min(0), Validators.max(999999)]],
      description: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(1000)]]
    });
  }

  loadProduct(id: string): void {
    this.isLoading = true;
    this.productService.getProduct(id).subscribe({
      next: (product) => {
        this.product = product;
        this.resetFormValues(product);
        this.checkOwnership();
        this.isLoading = false;
      },
      error: (err) => {
        this.error = err?.error?.errorMessage ?? 'Failed to load product details';
        this.isLoading = false;
      }
    });
  }

  private resetFormValues(product: Product): void {
    this.form.patchValue({
      name: product.name,
      price: product.price,
      quantity: product.quantity,
      description: product.description
    });

    // Populate image previews or set default fallback if array is empty
    if (product.imageUrls && product.imageUrls.length > 0) {
      this.imagePreviews = [...product.imageUrls];
    } else {
      this.imagePreviews = [this.DEFAULT_NO_IMAGE];
    }
    this.selectedImageIndex = 0;
    this.selectedFiles = [];
  }

  get currentImage(): string {
    if (this.imagePreviews.length > 0 && this.imagePreviews[this.selectedImageIndex]) {
      return this.imagePreviews[this.selectedImageIndex];
    }
    return this.DEFAULT_NO_IMAGE;
  }

  // Handle selecting new local files
  onImagesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const files = Array.from(input.files);
    
    // Check overall maximum limit (5 images max)
    if (this.imagePreviews.length + files.length > 6) {
      this.error = 'Maximum 5 images allowed per product.';
      return;
    }

    // Filter and add files
    files.forEach((file) => {
      this.selectedFiles.push(file);
      const reader = new FileReader();
      reader.onload = (e: ProgressEvent<FileReader>) => {
        if (e.target?.result) {
          // Replace placeholder if it was the only item
          if (this.imagePreviews.length === 1 && this.imagePreviews[0] === this.DEFAULT_NO_IMAGE) {
            this.imagePreviews = [];
          }
          this.imagePreviews.push(e.target.result as string);
          this.selectedImageIndex = this.imagePreviews.length - 1;
        }
      };
      reader.readAsDataURL(file);
    });

    // Reset file input so the same file can be selected again if needed
    input.value = '';
  }

  // Remove individual image (Allows removing ALL images)
  removeImage(index: number, event: MouseEvent): void {
    event.stopPropagation();

    const removedItem = this.imagePreviews[index];

    // Remove preview from gallery list
    this.imagePreviews.splice(index, 1);

    // If it was a newly added local file preview, remove it from selectedFiles array
    if (removedItem.startsWith('data:')) {
      const dataUrlCountBefore = this.imagePreviews
        .slice(0, index)
        .filter(img => img.startsWith('data:')).length;
      this.selectedFiles.splice(dataUrlCountBefore, 1);
    }

    // Adjust selected index safely
    if (this.selectedImageIndex >= this.imagePreviews.length) {
      this.selectedImageIndex = Math.max(0, this.imagePreviews.length - 1);
    }

    // If ALL images are deleted, display the frontend local no-image placeholder
    if (this.imagePreviews.length === 0) {
      this.imagePreviews = [this.DEFAULT_NO_IMAGE];
      this.selectedImageIndex = 0;
    }
  }

  selectImage(index: number): void {
    this.selectedImageIndex = index;
  }

  editProduct(): void {
    this.editing = true;
    this.error = null;
  }

  cancelEdit(): void {
    if (this.product) {
      this.resetFormValues(this.product);
    }
    this.editing = false;
    this.error = null;
  }

  saveProduct(): void {
  if (this.form.invalid || !this.product) return;

  this.isSaving = true;
  this.error = null;

  const values = this.form.getRawValue();
  const formData = new FormData();
  formData.append('name', values.name.trim());
  formData.append('description', values.description.trim());
  formData.append('price', values.price.toString());
  formData.append('quantity', values.quantity.toString());

  const existingRemoteUrls = this.imagePreviews.filter(
    (url) => !url.startsWith('data:') && url !== this.DEFAULT_NO_IMAGE
  );

  existingRemoteUrls.forEach((url) => {
    formData.append('existingImageUrls', url);
  });

  // 2. Append newly uploaded local files
  this.selectedFiles.forEach((file) => {
    formData.append('images', file);
  });

  this.productService.updateProduct(this.product.id!, formData).subscribe({
    next: (updatedProduct) => {
      this.product = updatedProduct;
      this.resetFormValues(updatedProduct);
      this.editing = false;
      this.isSaving = false;
    },
    error: (err) => {
      console.log(err);
      this.error = err?.error?.errorMessage ?? 'Failed to update product details';
      this.isSaving = false;
    }
  });
}
  openDeleteModal(): void {
    this.showDeleteModal = true;
  }

  closeDeleteModal(): void {
    this.showDeleteModal = false;
  }

  confirmDelete(): void {
    if (!this.product?.id) return;
    this.isDeleting = true;

    this.productService.deleteProduct(this.product.id).subscribe({
      next: () => {
        this.isDeleting = false;
        this.closeDeleteModal();
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.error = err?.error?.errorMessage ?? 'Failed to delete product';
        this.isDeleting = false;
        this.closeDeleteModal();
      }
    });
  }

  openFullImageView(): void {
    if (this.currentImage !== this.DEFAULT_NO_IMAGE) {
      this.isFullViewOpen = true;
    }
  }

  closeFullImageView(): void {
    this.isFullViewOpen = false;
  }

  prevImage(): void {
    if (this.imagePreviews.length === 0) return;
    this.selectedImageIndex =
      (this.selectedImageIndex - 1 + this.imagePreviews.length) % this.imagePreviews.length;
  }

  nextImage(): void {
    if (this.imagePreviews.length === 0) return;
    this.selectedImageIndex = (this.selectedImageIndex + 1) % this.imagePreviews.length;
  }

 downloadCurrentImage(): void {
  // Extract the image ID/filename from currentImage URL or product object
  const imageId = this.getImageId(this.currentImage); 
  if (!imageId) return;

  const downloadUrl = `api/media/images/${imageId}/download`;

  this.http.get(downloadUrl, { responseType: 'blob' }).subscribe({
    next: (blob: Blob) => {
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${this.product?.name || 'product'}-image.jpg`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    },
    error: (err) => console.error('Failed to download image:', err)
  });
}

// Helper to get filename/id from URL
private getImageId(imageUrl: string): string {
  if (!imageUrl) return '';
  return imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
}
  onPriceInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.value && input.value.includes('.')) {
      const parts = input.value.split('.');
      if (parts[1].length > 2) {
        input.value = `${parts[0]}.${parts[1].slice(0, 2)}`;
        this.form.get('price')?.setValue(parseFloat(input.value));
      }
    }
  }

  goBack(): void {
    this.router.navigate(['/']);
  }

 private checkOwnership(): void {
  this.userService.user$.subscribe((user) => {
    if (!user || !this.product) {
      this.isOwner = false;
      return;
    }

    const ownerId = this.product.userId;
    this.isOwner = !!(user.id && ownerId && user.id === ownerId);
  });
}
}