/**
 * Crops a PNG image to 200x200 pixels from the top-left corner
 * @param imageData Uint8Array containing the PNG image data
 * @returns Promise resolving to a Uint8Array of the cropped PNG image
 */
export function cropPngTo200x200(imageData: Uint8Array): Promise<Uint8Array> {
    return new Promise((resolve, reject) => {
        // Create blob and object URL
        const blob = new Blob([imageData], { type: 'image/png' });
        const imageUrl = URL.createObjectURL(blob);

        // Create image element to load the PNG
        const img = new Image();
        img.onload = () => {
            // No need to crop
            if (img.naturalWidth <= 200 && img.naturalHeight <= 200) {
                resolve(imageData);
                return;
            }

            // Set crop dimensions - always 200x200 from top-left
            const cropWidth = 200;
            const cropHeight = 200;

            // Create canvas for cropping
            const canvas = document.createElement('canvas');
            canvas.width = cropWidth;
            canvas.height = cropHeight;

            // Draw cropped portion to canvas
            const ctx = canvas.getContext('2d');
            if (!ctx) {
                reject(new Error('Could not create canvas context'));
                return;
            }

            ctx.drawImage(
                img,
                0, 0, cropWidth, cropHeight,  // Source rectangle (top-left)
                0, 0, cropWidth, cropHeight   // Destination rectangle
            );

            // Convert canvas to blob
            canvas.toBlob((blob) => {
                if (!blob) {
                    reject(new Error('Failed to create blob from canvas'));
                    return;
                }

                // Convert blob to Uint8Array
                const reader = new FileReader();
                reader.onload = () => {
                    if (!reader.result) {
                        reject(new Error('Failed to read blob data'));
                        return;
                    }

                    const arrayBuffer = reader.result as ArrayBuffer;
                    const croppedData = new Uint8Array(arrayBuffer);
                    resolve(croppedData);
                };

                reader.onerror = () => reject(new Error('Error reading blob data'));
                reader.readAsArrayBuffer(blob);
            }, 'image/png');

            // Clean up
            URL.revokeObjectURL(imageUrl);
        };

        img.onerror = () => {
            URL.revokeObjectURL(imageUrl);
            reject(new Error('Failed to load the image'));
        };

        img.src = imageUrl;
    });
}
