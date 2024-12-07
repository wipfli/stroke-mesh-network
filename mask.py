from PIL import Image, ImageOps
import numpy as np

# Load the image
image_path = "lines.png"  # Replace with your image path
image = Image.open(image_path).convert("L")  # Convert to grayscale

# Convert the image to a numpy array
img_array = np.array(image)

# Define the inverted mask (0 is black, 1 is white)
input_masks = [
    np.array([
        [0, 0, 0],
        [1, 0, 1],
        [1, 1, 1]
    ]),
    np.array([
        [0, 0, 0],
        [0, 0, 1],
        [1, 1, 1]
    ]),
    np.array([
        [0, 0, 0],
        [1, 0, 0],
        [1, 1, 0]
    ]),
    # np.array([
    #     [1, 0, 1],
    #     [1, 0, 1],
    #     [1, 1, 1]
    # ]),
    # np.array([
    #     [1, 0, 0],
    #     [1, 0, 1],
    #     [1, 1, 1]
    # ]),
    np.array([
        [0, 0, 0],
        [0, 0, 0],
        [1, 1, 1]
    ]),
    # np.array([
    #     [1, 1, 0],
    #     [1, 0, 1],
    #     [0, 0, 0]
    # ]),
    # np.array([
    #     [0, 1, 1],
    #     [1, 0, 1],
    #     [1, 0, 0]
    # ]),
    np.array([
        [1, 1, 1],
        [1, 0, 0],
        [1, 0, 0]
    ]),
    np.array([
        [1, 1, 1],
        [1, 0, 0],
        [0, 0, 0]
    ]),
    # np.array([
    #     [1, 1, 0],
    #     [0, 0, 0],
    #     [0, 0, 0]
    # ]),
]

def generate_unique_masks(input_masks):
    """Generate all rotations and mirrors of the masks and keep only unique ones."""
    unique_masks = set()
    
    for mask in input_masks:
        # Convert mask to a tuple for immutability (needed for set operations)
        mask_tuple = tuple(map(tuple, mask))
        
        # Generate rotations (90°, 180°, 270°)
        rotations = [mask, np.rot90(mask), np.rot90(mask, 2), np.rot90(mask, 3)]
        
        # Generate mirrors (horizontal and vertical flips)
        mirror = np.fliplr(mask)
        mirror_rotations = [mirror, np.rot90(mirror), np.rot90(mirror, 2), np.rot90(mirror, 3)]
        
        # Add all variations to the set
        for variation in rotations + mirror_rotations:
            unique_masks.add(tuple(map(tuple, variation)))
    
    # Convert back to numpy arrays and return as a list
    return [np.array(mask) for mask in unique_masks]

masks = []
for input_mask in input_masks:
    masks += generate_unique_masks([input_mask])

# Get the dimensions of the image
rows, cols = img_array.shape

# Create a copy of the image array for output
output_array = img_array.copy()

# Threshold for binary comparison
threshold = 128  # Pixels below this value are black; above are white

# Loop over the image, applying the mask
iterations = 5
for _ in range(iterations):
    for i in range(1, rows - 1):
        for j in range(1, cols - 1):
            # Extract the 3x3 region
            region = img_array[i-1:i+2, j-1:j+2]

            # Convert the region to binary (0 for black, 1 for white)
            binary_region = (region > threshold).astype(int)

            # Check if the region matches any mask
            for mask in masks:
                if np.array_equal(binary_region, mask):# and img_array[i, j] != 64:
                    img_array[i, j] = 255  # Set the center pixel to white
                    break  # Exit the mask loop if a match is found

# Convert the output array back to an image
output_image = Image.fromarray(img_array)

# Save or show the resulting image
output_image.save("output_image.png")
output_image.show()
