/**
 * Text for the screen from an API field, '' when the field is empty.
 *
 * The generated models mark every field optional ("string | undefined"), but the backend sends
 * missing values as null ("categoryId": null), so both count as empty. A null left in a form field
 * would match no option of a dropdown, which then shows blank.
 */
export function textOrEmpty(value: string | undefined | null): string {
  if (value === undefined || value === null) {
    return '';
  }
  return value;
}
