package fc.xml.diff.encode;

import static fc.xml.diff.Segment.Operation.INSERT;
import static fc.xml.diff.Segment.Operation.UPDATE;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;

import fc.util.StringUtil;
import fc.util.Util;
import fc.xml.diff.Segment;
import fc.xml.xas.Item;


/**
 * LowLevelEncoder produces low-level debug output showing how the sequences
 * from each document align side by side. Useful for observing what is going on.
 */
public class LowLevelEncoder implements DiffEncoder {
    
    enum SectionPos {
        SECTION_START("\\"),
        SECTION_MIDDLE("|"),
        SECTION_END("/"),
        NONE(" ");
        
        private final String marker;
        
        private SectionPos(String marker) {
            this.marker = marker;
        }
        
        public String getMarker() {
            return marker;
        }
    }

    public void encodeDiff(List<Item> base, List<Item> doc, List<Segment<Item>> matches,
                           List<Item> preamble, OutputStream out) throws IOException {
        encodeDiff(base, doc, matches, preamble, out, DEFAULT_PT, DEFAULT_PT);
    }


    public static <E> void encodeDiff(List<E> base, List<E> doc, List<Segment<E>> matches,
                                      List<E> preamble, OutputStream out, PosTransformer lpt,
                                      PosTransformer rpt) {
        PrintWriter pw = new PrintWriter(out);

        pw.println("<fc:diff xmlns:fc='faxma'>");

        for (int pos = 0; pos < base.size();) {
            // find corresponding match (+trailing ins)
            Segment<E> match = null;
            boolean found = false;
            for (Segment<E> s : matches) {
                if (s.getOp() == INSERT && match != null) {
                    // dump ins (ins dumped after copies)
                    for (int i = 0; i < s.getInsert().size(); i++) {
                        pw.println("<fc:INS>");
                        emitLine(pw, -1, "-", s.getInsert().get(i), s.getOp() == UPDATE,
                                 s.getPosition() + i, lpt, rpt, SectionPos.NONE);
                        pw.println("</fc:INS>");
                    }
                } else if (s.getOffset() == pos && s.getOp() != INSERT) {
                    found = true;
                    match = s;
                    int slen = s.getLength();
                    for (int i = 0; i < slen; i++) {
                        Object updated = null;

                        if (s.getOp() == UPDATE)
                            updated = i < s.getInsert().size() ? s.getInsert().get(i) : "-";
                        else
                            updated = base.get(pos);

                        SectionPos sectionPos = (slen == 1) ? SectionPos.NONE :
                            (i == 0) ? SectionPos.SECTION_START : 
                            (i == slen - 1) ? SectionPos.SECTION_END : SectionPos.SECTION_MIDDLE; 

                        if (sectionPos.equals(SectionPos.SECTION_START)) {
                            pw.println("<fc:MOVE-SEQ>");
                        }

                        /*
                        // For long copied sections, print only the first & last 3 lines, and dots in between
                        if (i > 2 && slen > 9 && i < slen - 3 && s.getOp() != UPDATE) {
                            if (i < 6) {
                                String dots = (i != 4) ? ":" : "(skipped) :";
                                pw.println(StringUtil.format(dots, -(EVENT_COLWIDTH + POS_COLWIDTH + 2)) +
                                           StringUtil.format(SectionPos.SECTION_MIDDLE.getMarker(), -(EVENT_COLWIDTH + POS_COLWIDTH + 5)));
                            }
                        } else */
                        emitLine(pw, pos, base.get(pos), updated, s.getOp() == UPDATE,
                                 s.getPosition() + i, lpt, rpt, sectionPos);

                        if (sectionPos.equals(SectionPos.SECTION_END)) {
                            pw.println("</fc:MOVE-SEQ>");
                        }

                        pos++;
                    }
                    // update && ins > basematch
                    if (s.getOp() == UPDATE) {
                        for (int i = s.getLength(); i < s.getInsert().size(); i++) {
                            pw.println("<fc:UPDATE>");
                            emitLine(pw, -1, "-", s.getInsert().get(i), s.getOp() == UPDATE,
                                     s.getPosition() + i, lpt, rpt, SectionPos.NONE);
                            pw.println("</fc:UPDATE>");
                        }
                    }
                } else match = null;
            }
            if (!found) {
                // Dump del
                pw.println("<fc:DEL>");
                emitLine(pw, pos, base.get(pos), "-", false, -1, lpt, rpt, SectionPos.NONE);
                pw.println("</fc:DEL>");
                pos++;
            }
        }

        pw.println("</fc:diff>");

        pw.flush();
    }

    public static final int EVENT_COLWIDTH = 40;
    public static final int POS_COLWIDTH = 6;


    private static void emitLine(PrintWriter out, int pos, Object base, Object mod, boolean update,
                                 int rpos, PosTransformer lpt, PosTransformer rpt, SectionPos sectionPos) {
        String baseS = "";
        String brS = "";

        if (base instanceof Item) {
            baseS = ((Item) base).toStringXML();
        } else {
            baseS = base.toString();
        }

        if (mod instanceof Item) {
            brS = ((Item) mod).toStringXML();
        } else {
            brS = mod.toString();
        }

        if (baseS.equals("-")) { baseS = ""; }
        if (brS.equals("-")) { brS = ""; }

        out.print("<!-- ");
        out.print(lpt.transform(pos) + "/" +
                  rpt.transform(rpos) + " " +
                  sectionPos.getMarker());
        out.print(" -->");

        out.print(" ");

        String changes = "";
        if (!baseS.isEmpty()) { changes += baseS; }
        if (!baseS.isEmpty() && !brS.isEmpty()) { changes += " <=> "; }
        if (!brS.isEmpty()) { changes += brS + (update ? " *" : "  "); }

        if (baseS.equals(brS)) {
            changes = "<fc:MOVE from='" + pos + "' to='" + rpos + "'>" + baseS + "</fc:MOVE>";
        }

        out.println(changes);
    }

    public static final PosTransformer DEFAULT_PT = new DefaultPosTranformer();

}
// arch-tag: 7d4784b9-0276-42e4-8cd4-c40d38c18c43
//
