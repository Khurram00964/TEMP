import { StudySheetApp } from "@/src/components/StudySheetApp";
import { ThemeProvider } from "@/src/components/ThemeContext";

export default function Page() {
  return (
    <ThemeProvider>
      <StudySheetApp />
    </ThemeProvider>
  );
}
